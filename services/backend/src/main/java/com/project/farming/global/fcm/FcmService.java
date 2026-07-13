package com.project.farming.global.fcm;

import java.util.List;

/**
 * FCM 메시지 발송 기능 인터페이스
 */
public interface FcmService {

    /**
     * 특정 기기(사용자)의 FCM 토큰으로 알림 메시지를 발송합니다.
     *
     * @param targetToken 발송 대상의 FCM 토큰
     * @param title       알림 제목
     * @param body        알림 내용
     */
    void sendMessageTo(String targetToken, String title, String body);
    void sendMessagesTo(List<String> targetTokens, String title, String body);
    List<FcmBatchResult> sendBatch(List<FcmBatchMessage> messages);
}
