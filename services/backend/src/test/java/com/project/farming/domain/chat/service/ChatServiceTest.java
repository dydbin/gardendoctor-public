package com.project.farming.domain.chat.service;

import com.project.farming.domain.chat.dto.PythonChatPayload;
import com.project.farming.domain.chat.dto.PythonSessionPayload;
import com.project.farming.domain.chat.entity.Chat;
import com.project.farming.domain.chat.outbox.ChatDeletionOutboxService;
import com.project.farming.domain.chat.repository.ChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatAiClient chatAiClient;
    @Mock
    private ChatDeletionOutboxService deletionOutboxService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRepository, chatAiClient, deletionOutboxService);
    }

    @Test
    void roomListShouldSendOnlyOwnedSessionIdsToAiServer() {
        Chat first = chat(1L, 7L, 101L);
        Chat second = chat(2L, 7L, 102L);
        Pageable pageable = PageRequest.of(0, 20);
        when(chatRepository.findByUserIdOrderByChatIdDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(second, first), pageable, 2));
        when(chatAiClient.getSessions(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(session(101L), session(102L)));

        var result = chatService.getChatRoomList(7L, pageable);

        assertThat(result.content()).extracting("chatId").containsExactly(2L, 1L);

        verify(chatAiClient).getSessions(org.mockito.ArgumentMatchers.argThat(
                ids -> ids.size() == 2 && ids.containsAll(List.of(101L, 102L))));
    }

    @Test
    void messagePageShouldPropagateOffsetLimitAndRoleToAiServer() {
        Pageable pageable = PageRequest.of(1, 10);
        Chat chat = chat(1L, 7L, 101L);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));
        PythonChatPayload.PythonChatResponse aiResponse = response(101L, "answer");
        aiResponse.setTotalMessages(21L);
        when(chatAiClient.getSession(101L, 10L, 10, "assistant")).thenReturn(aiResponse);

        var result = chatService.getAssistantMessages(7L, 1L, pageable);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.content()).extracting("role").containsExactly("assistant");
    }

    @Test
    void deleteShouldCommitOutboxIntentBeforeRemovingLocalChatWithoutCallingAi() {
        Chat chat = chat(1L, 7L, 101L);
        when(chatRepository.findById(1L)).thenReturn(Optional.of(chat));

        chatService.deleteChatRoom(7L, 1L);

        InOrder order = inOrder(deletionOutboxService, chatRepository);
        order.verify(deletionOutboxService).enqueue(101L);
        order.verify(chatRepository).delete(chat);
        verify(chatAiClient, never()).deleteSession(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void newChatShouldPersistOnlyAfterBoundedAiCallReturnsSessionId() {
        PythonChatPayload.PythonChatResponse aiResponse = response(501L, "answer");
        when(chatAiClient.ask(null, "question")).thenReturn(aiResponse);
        when(chatRepository.save(org.mockito.ArgumentMatchers.any(Chat.class)))
                .thenReturn(chat(12L, 7L, 501L));

        var response = chatService.askPythonAgent(7L, null, "question");

        assertThat(response.getChatId()).isEqualTo(12L);
        assertThat(response.getAnswer()).isEqualTo("answer");
    }

    private Chat chat(Long chatId, Long userId, Long pythonSessionId) {
        return Chat.builder()
                .chatId(chatId)
                .userId(userId)
                .pythonSessionId(pythonSessionId)
                .build();
    }

    private PythonChatPayload.PythonChatResponse response(Long sessionId, String answer) {
        PythonChatPayload.PythonChatMessage message = new PythonChatPayload.PythonChatMessage();
        message.setRole("assistant");
        message.setQuery(answer);
        PythonChatPayload.PythonChatResponse response = new PythonChatPayload.PythonChatResponse();
        response.setId(sessionId);
        response.setMessages(List.of(message));
        return response;
    }

    private PythonSessionPayload session(Long sessionId) {
        PythonSessionPayload session = new PythonSessionPayload();
        session.setId(sessionId);
        session.setQuery("question");
        session.setCreatedAt("2026-07-10T10:00:00");
        session.setUpdatedAt("2026-07-10T10:01:00");
        session.setMessageCount(2);
        return session;
    }
}
