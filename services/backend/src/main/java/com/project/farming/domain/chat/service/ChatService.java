package com.project.farming.domain.chat.service;

import com.project.farming.domain.chat.dto.ChatMessageResponse;
import com.project.farming.domain.chat.dto.ChatResponse;
import com.project.farming.domain.chat.dto.ChatRoomResponse;
import com.project.farming.domain.chat.dto.PythonChatPayload;
import com.project.farming.domain.chat.dto.PythonSessionPayload;
import com.project.farming.domain.chat.entity.Chat;
import com.project.farming.domain.chat.outbox.ChatDeletionOutboxService;
import com.project.farming.domain.chat.repository.ChatRepository;
import com.project.farming.global.ai.AiServiceUnavailableException;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatAiClient chatAiClient;
    private final ChatDeletionOutboxService chatDeletionOutboxService;

    public ChatResponse askPythonAgent(Long userId, Long chatId, String question) {
        Long pythonSessionId = chatId == null ? null : resolveOwnedPythonSessionId(userId, chatId);
        PythonChatPayload.PythonChatResponse responseFromPython = chatAiClient.ask(pythonSessionId, question);

        requirePythonChatResponse(responseFromPython);
        String answer = responseFromPython.getMessages().stream()
                .filter(message -> "assistant".equalsIgnoreCase(message.getRole()))
                .reduce((first, second) -> second)
                .map(PythonChatPayload.PythonChatMessage::getQuery)
                .orElse("답변을 찾을 수 없습니다.");

        Long resolvedChatId = chatId;
        if (chatId == null) {
            requirePythonSessionId(responseFromPython);
            Chat savedChat = chatRepository.save(Chat.builder()
                    .userId(userId)
                    .pythonSessionId(responseFromPython.getId())
                    .build());
            resolvedChatId = savedChat.getChatId();
        }

        return ChatResponse.builder()
                .answer(answer)
                .question(question)
                .chatId(resolvedChatId)
                .build();
    }

    public PageResponse<ChatMessageResponse> getAssistantMessages(
            Long userId, Long chatId, Pageable pageable) {
        return getMessages(userId, chatId, "assistant", pageable);
    }

    public PageResponse<ChatMessageResponse> getAllMessages(
            Long userId, Long chatId, Pageable pageable) {
        return getMessages(userId, chatId, null, pageable);
    }

    public PageResponse<ChatRoomResponse> getChatRoomList(Long userId, Pageable pageable) {
        Page<Chat> chats = chatRepository.findByUserIdOrderByChatIdDesc(
                userId, PageRequestPolicy.stable(pageable));
        Map<Long, Long> springChatToPythonSessionMap = chats.getContent().stream()
                .collect(Collectors.toMap(
                        Chat::getPythonSessionId,
                        Chat::getChatId,
                        (firstChatId, ignored) -> firstChatId));

        List<PythonSessionPayload> pythonSessions = chatAiClient.getSessions(
                List.copyOf(springChatToPythonSessionMap.keySet()));
        Map<Long, PythonSessionPayload> pythonSessionById = pythonSessions == null
                ? Map.of()
                : pythonSessions.stream().collect(Collectors.toMap(
                        PythonSessionPayload::getId,
                        session -> session,
                        (first, ignored) -> first));

        List<ChatRoomResponse> responses = chats.getContent().stream()
                .map(chat -> toChatRoomResponse(chat, pythonSessionById.get(chat.getPythonSessionId())))
                .toList();
        return PageResponse.from(chats, responses);
    }

    @Transactional
    public void deleteChatRoom(Long userId, Long chatId) {
        Chat chat = findOwnedChat(userId, chatId);
        chatDeletionOutboxService.enqueue(chat.getPythonSessionId());
        chatRepository.delete(chat);
    }

    private Chat findOwnedChat(Long userId, Long chatId) {
        return chatRepository.findById(chatId)
                .filter(chat -> isOwnedBy(chat, userId))
                .orElseThrow(() -> new IllegalArgumentException("해당 채팅 기록을 조회할 수 없습니다."));
    }

    private Long resolveOwnedPythonSessionId(Long userId, Long chatId) {
        return findOwnedChat(userId, chatId).getPythonSessionId();
    }

    private PageResponse<ChatMessageResponse> getMessages(
            Long userId, Long chatId, String role, Pageable pageable) {
        Pageable stablePageable = PageRequestPolicy.stable(pageable);
        PythonChatPayload.PythonChatResponse response = chatAiClient.getSession(
                resolveOwnedPythonSessionId(userId, chatId),
                stablePageable.getOffset(),
                stablePageable.getPageSize(),
                role);
        List<ChatMessageResponse> messages = toChatMessageResponses(response);
        long totalMessages = response == null || response.getTotalMessages() == null
                ? messages.size()
                : response.getTotalMessages();
        boolean hasNext = stablePageable.getOffset() + messages.size() < totalMessages;
        return PageResponse.of(
                messages,
                stablePageable.getPageNumber(),
                stablePageable.getPageSize(),
                hasNext,
                totalMessages);
    }

    private ChatRoomResponse toChatRoomResponse(Chat chat, PythonSessionPayload pythonSession) {
        if (pythonSession == null) {
            throw new AiServiceUnavailableException(
                    "AI 서버에서 로컬 채팅 세션을 찾을 수 없습니다: " + chat.getChatId(), null);
        }
        return ChatRoomResponse.builder()
                .chatId(chat.getChatId())
                .pythonSessionId(pythonSession.getId())
                .query(pythonSession.getQuery())
                .createdAt(pythonSession.getCreatedAt())
                .updatedAt(pythonSession.getUpdatedAt())
                .messageCount(pythonSession.getMessageCount())
                .build();
    }

    private boolean isOwnedBy(Chat chat, Long userId) {
        return Objects.equals(chat.getUserId(), userId);
    }

    private void requirePythonChatResponse(PythonChatPayload.PythonChatResponse response) {
        if (response == null || response.getMessages() == null) {
            throw new IllegalStateException("챗봇 서버 응답이 비어 있습니다.");
        }
    }

    private void requirePythonSessionId(PythonChatPayload.PythonChatResponse response) {
        if (response.getId() == null) {
            throw new IllegalStateException("챗봇 서버가 세션 ID를 반환하지 않았습니다.");
        }
    }

    private List<ChatMessageResponse> toChatMessageResponses(
            PythonChatPayload.PythonChatResponse response) {
        if (response == null || response.getMessages() == null) {
            return List.of();
        }

        return response.getMessages().stream()
                .map(message -> ChatMessageResponse.builder()
                        .id(message.getId())
                        .role(message.getRole())
                        .query(message.getQuery())
                        .timestamp(message.getTimestamp())
                        .build())
                .toList();
    }
}
