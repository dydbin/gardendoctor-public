package com.project.farming.global.fcm;

import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Firebase Admin SDK 기반 FCM 메시지 발송 구현체
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FcmServiceImpl implements FcmService {

    private static final int MAX_MULTICAST_TOKENS = 500;

    private final FirebaseMessagingGateway firebaseMessagingGateway;

    @Override
    public void sendMessageTo(String targetToken, String title, String body) {
        if (targetToken == null || targetToken.isBlank()) {
            log.warn("⚠️ FCM target token is empty. Skipping push notification.");
            return;
        }

        // 사용자에게 표시될 알림 생성
        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        // 메시지 구성
        Message message = Message.builder()
                .setToken(targetToken)
                .setNotification(notification)
                // 필요 시 추가 데이터 전달 가능
                // .putData("key", "value")
                .build();

        try {
            String response = firebaseMessagingGateway.send(message);
            log.info("✅ Successfully sent FCM message to [{}]: {}", maskToken(targetToken), response);
        } catch (FirebaseMessagingException e) {
            FcmSendException sendException = toFcmSendException(e);
            log.error("🔥 FCM send failed [{}]: {}", maskToken(targetToken), sendException.getMessage());
            if (sendException.isPermanentFailure()) {
                log.warn("   -> Token is invalid/unregistered, consider removing from user record.");
            }
            throw sendException;
        } catch (Exception e) {
            log.error("🔥 Unexpected FCM error [{}]", maskToken(targetToken), e);
            throw FcmSendException.retryable("Unexpected FCM error: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendMessagesTo(List<String> targetTokens, String title, String body) {
        if (targetTokens == null || targetTokens.isEmpty()) {
            log.warn("FCM target token list is empty. Skipping multicast push notification.");
            return;
        }

        List<String> validTokens = targetTokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .toList();
        if (validTokens.isEmpty()) {
            log.warn("FCM target token list has no valid token. Skipping multicast push notification.");
            return;
        }

        for (int start = 0; start < validTokens.size(); start += MAX_MULTICAST_TOKENS) {
            int end = Math.min(start + MAX_MULTICAST_TOKENS, validTokens.size());
            sendMessageChunk(validTokens.subList(start, end), title, body);
        }
    }

    @Override
    public List<FcmBatchResult> sendBatch(List<FcmBatchMessage> batchMessages) {
        if (batchMessages == null || batchMessages.isEmpty()) {
            return List.of();
        }
        if (batchMessages.size() > MAX_MULTICAST_TOKENS) {
            throw new IllegalArgumentException("FCM batch size must not exceed " + MAX_MULTICAST_TOKENS);
        }

        List<Message> messages = batchMessages.stream()
                .map(this::toFirebaseMessage)
                .toList();
        BatchResponse response;
        try {
            response = firebaseMessagingGateway.sendEach(messages);
        } catch (FirebaseMessagingException e) {
            throw toFcmSendException(e);
        } catch (Exception e) {
            throw FcmSendException.retryable("Unexpected FCM batch error: " + e.getMessage(), e);
        }

        List<SendResponse> responses = response.getResponses();
        if (responses.size() != batchMessages.size()) {
            throw FcmSendException.retryable(
                    "FCM batch response size mismatch: expected=" + batchMessages.size()
                            + ", actual=" + responses.size(),
                    null
            );
        }

        List<FcmBatchResult> results = new ArrayList<>(responses.size());
        for (int index = 0; index < responses.size(); index++) {
            SendResponse sendResponse = responses.get(index);
            FcmBatchMessage batchMessage = batchMessages.get(index);
            if (sendResponse.isSuccessful()) {
                results.add(FcmBatchResult.success(batchMessage.correlationId()));
                continue;
            }

            FirebaseMessagingException exception = sendResponse.getException();
            MessagingErrorCode errorCode = exception == null ? null : exception.getMessagingErrorCode();
            String errorMessage = exception == null
                    ? "FCM batch item failed without an error response"
                    : toFcmSendException(exception).getMessage();
            results.add(FcmBatchResult.failure(
                    batchMessage.correlationId(),
                    isPermanentFailure(errorCode),
                    errorMessage
            ));
        }
        return results;
    }

    private Message toFirebaseMessage(FcmBatchMessage batchMessage) {
        Message.Builder builder = Message.builder()
                .setToken(batchMessage.targetToken())
                .setNotification(Notification.builder()
                        .setTitle(batchMessage.title())
                        .setBody(batchMessage.body())
                        .build());
        if (batchMessage.eventId() != null && !batchMessage.eventId().isBlank()) {
            builder.putData("eventId", batchMessage.eventId());
        }
        return builder.build();
    }

    private void sendMessageChunk(List<String> targetTokens, String title, String body) {
        MulticastMessage message = MulticastMessage.builder()
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .addAllTokens(targetTokens)
                .build();

        BatchResponse response;
        try {
            response = firebaseMessagingGateway.sendEachForMulticast(message);
            log.info("Messages send result - Target: {}, Success: {}, Failure: {}",
                    targetTokens.size(), response.getSuccessCount(), response.getFailureCount());
        } catch (FirebaseMessagingException e) {
            FcmSendException sendException = toFcmSendException(e);
            log.error("Messages send failed: {}", sendException.getMessage());
            throw sendException;
        }

        if (response.getFailureCount() > 0) {
            checkFailure(response, targetTokens);
        }
    }

    private void checkFailure(BatchResponse response, List<String> targetTokens) {
        List<SendResponse> responses = response.getResponses();
        List<String> failedTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                failedTokens.add(maskToken(targetTokens.get(i)));
            }
        }
        log.warn("Failed to send messages: {}", failedTokens);
        // 공지 broadcast 재시도는 FcmOutboxProcessor의 단건 발송 경로에서 처리합니다.
    }

    private FcmSendException toFcmSendException(FirebaseMessagingException e) {
        MessagingErrorCode messagingErrorCode = e.getMessagingErrorCode();
        String errorCode = messagingErrorCode == null ? "UNKNOWN" : messagingErrorCode.name();
        String message = "FCM send failed [" + errorCode + "]: " + e.getMessage();
        if (isPermanentFailure(messagingErrorCode)) {
            return FcmSendException.permanent(message, e);
        }
        return FcmSendException.retryable(message, e);
    }

    private boolean isPermanentFailure(MessagingErrorCode messagingErrorCode) {
        return messagingErrorCode == MessagingErrorCode.INVALID_ARGUMENT
                || messagingErrorCode == MessagingErrorCode.SENDER_ID_MISMATCH
                || messagingErrorCode == MessagingErrorCode.UNREGISTERED;
    }

    /**
     * FCM 토큰 마스킹 처리
     */
    private String maskToken(String token) {
        if (token == null) {
            return "null";
        }
        if (token.length() < 10) return token;
        return token.substring(0, 5) + "..." + token.substring(token.length() - 5);
    }
}
