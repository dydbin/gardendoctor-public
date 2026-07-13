package com.project.farming.domain.chat.controller;

import com.project.farming.domain.chat.dto.ChatMessageResponse;
import com.project.farming.domain.chat.dto.ChatRequest;
import com.project.farming.domain.chat.dto.ChatResponse;
import com.project.farming.domain.chat.dto.ChatRoomResponse;
import com.project.farming.domain.chat.service.ChatService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatService);
    }

    @Test
    void chatShouldWrapResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        ChatRequest request = new ChatRequest();
        request.setChatId(10L);
        request.setQuery("토마토 상태 알려줘");
        ChatResponse chatResponse = ChatResponse.builder()
                .chatId(10L)
                .question(request.getQuery())
                .answer("정상입니다.")
                .build();
        when(chatService.askPythonAgent(1L, 10L, request.getQuery())).thenReturn(chatResponse);

        ResponseEntity<CommonResponse<ChatResponse>> response = chatController.chat(request, userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("챗봇 응답 성공");
        assertThat(response.getBody().getData()).isSameAs(chatResponse);
    }

    @Test
    void messageLookupsShouldWrapPagesInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<ChatMessageResponse> messages = PageResponse.of(List.of(ChatMessageResponse.builder()
                .id(1L)
                .role("assistant")
                .query("답변")
                .timestamp("2026-07-10T10:00:00")
                .build()), 0, 20, false, 1);
        when(chatService.getAssistantMessages(1L, 10L, pageable)).thenReturn(messages);
        when(chatService.getAllMessages(1L, 10L, pageable)).thenReturn(messages);

        ResponseEntity<CommonResponse<PageResponse<ChatMessageResponse>>> assistantOnly =
                chatController.getChatSessionMessages(userDetails, 10L, pageable);
        ResponseEntity<CommonResponse<PageResponse<ChatMessageResponse>>> all =
                chatController.getChatSessionMessagesAll(userDetails, 10L, pageable);

        assertThat(assistantOnly.getBody()).isNotNull();
        assertThat(assistantOnly.getBody().getData()).isSameAs(messages);
        assertThat(all.getBody()).isNotNull();
        assertThat(all.getBody().getData()).isSameAs(messages);
    }

    @Test
    void getChatRoomListShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<ChatRoomResponse> rooms = PageResponse.of(List.of(ChatRoomResponse.builder()
                .chatId(10L)
                .pythonSessionId(20L)
                .query("질문")
                .createdAt("2026-07-10T10:00:00")
                .updatedAt("2026-07-10T10:05:00")
                .messageCount(2)
                .build()), 0, 20, false, 1);
        when(chatService.getChatRoomList(1L, pageable)).thenReturn(rooms);

        ResponseEntity<CommonResponse<PageResponse<ChatRoomResponse>>> response =
                chatController.getChatRoomList(userDetails, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(rooms);
    }

    @Test
    void deleteChatRoomShouldReturnNoContent() {
        CustomUserDetails userDetails = userDetails();

        ResponseEntity<CommonResponse<Void>> response = chatController.deleteChatRoom(userDetails, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(chatService).deleteChatRoom(1L, 10L);
    }

    @Test
    void missingPrincipalShouldReturnCommonUnauthorizedBody() {
        ResponseEntity<CommonResponse<PageResponse<ChatRoomResponse>>> response =
                chatController.getChatRoomList(null, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(chatService);
    }

    private CustomUserDetails userDetails() {
        User user = User.builder()
                .userId(1L)
                .email("user@example.com")
                .password("password")
                .nickname("user")
                .role(UserRole.USER)
                .subscriptionStatus("ACTIVE")
                .build();
        return new CustomUserDetails(user);
    }
}
