package com.project.farming.domain.chat.controller;

import com.project.farming.domain.chat.dto.ChatRequest;
import com.project.farming.domain.chat.dto.ChatMessageResponse;
import com.project.farming.domain.chat.dto.ChatResponse;
import com.project.farming.domain.chat.dto.ChatRoomResponse;
import com.project.farming.domain.chat.service.ChatService;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chat API", description = "작물 챗봇 질문 및 답변 API")
@SecurityRequirement(name = "jwtAuth")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "챗봇 질문 전송", description = "사용자의 질문을 Python 챗봇 서버로 보내고 응답을 반환합니다. 대화 맥락이 유지됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "챗봇 응답 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping
    public ResponseEntity<CommonResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest requestBody,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null || userDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = userDetails.getUser().getUserId();

        ChatResponse response = chatService.askPythonAgent(
                userId,
                requestBody.getChatId(), // 클라이언트가 보낸 chatId
                requestBody.getQuery()
        );

        return ResponseEntity.ok(CommonResponse.success("챗봇 응답 성공", response));
    }

    @Operation(summary = "특정 대화의 답변만 조회", description = "특정 세션의 챗봇 답변(assistant)을 시간순으로 페이지 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "챗봇 답변 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/history/messages")
    public ResponseEntity<CommonResponse<PageResponse<ChatMessageResponse>>> getChatSessionMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("chatId") Long chatId,
            @PageableDefault(size = 20) Pageable pageable) {

        if (userDetails == null || userDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = userDetails.getUser().getUserId();
        PageResponse<ChatMessageResponse> sessionMessages = chatService.getAssistantMessages(
                userId, chatId, pageable);

        return ResponseEntity.ok(CommonResponse.success("챗봇 답변 목록 조회 성공", sessionMessages));
    }
    @Operation(summary = "특정 채팅방의 전체 메시지 조회", description = "특정 채팅방의 user와 assistant 메시지를 시간순으로 페이지 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "챗봇 전체 메시지 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/history/messages/all")
    public ResponseEntity<CommonResponse<PageResponse<ChatMessageResponse>>> getChatSessionMessagesAll(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("chatId") Long chatId,
            @PageableDefault(size = 20) Pageable pageable) {

        if (userDetails == null || userDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = userDetails.getUser().getUserId();
        PageResponse<ChatMessageResponse> sessionMessages = chatService.getAllMessages(
                userId, chatId, pageable);

        return ResponseEntity.ok(CommonResponse.success("챗봇 전체 메시지 조회 성공", sessionMessages));
    }

    @Operation(summary = "챗봇 대화방 목록 조회", description = "사용자의 대화방을 생성 역순으로 조회합니다. Spring의 chatId와 Python의 sessionId를 함께 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "챗봇 대화방 목록 조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping("/sessions")
    public ResponseEntity<CommonResponse<PageResponse<ChatRoomResponse>>> getChatRoomList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PageableDefault(size = 20) Pageable pageable) {

        if (userDetails == null || userDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = userDetails.getUser().getUserId();
        PageResponse<ChatRoomResponse> chatRooms = chatService.getChatRoomList(userId, pageable);

        return ResponseEntity.ok(CommonResponse.success("챗봇 대화방 목록 조회 성공", chatRooms));
    }

    @Operation(summary = "챗봇 대화방 삭제", description = "특정 챗봇 대화방을 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "챗봇 대화방 삭제 성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "챗봇 대화방을 찾을 수 없음")
    })
    @DeleteMapping("/{chatId}")
    public ResponseEntity<CommonResponse<Void>> deleteChatRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long chatId) {

        if (userDetails == null || userDetails.getUser() == null) {
            return unauthorized();
        }
        Long userId = userDetails.getUser().getUserId();
        chatService.deleteChatRoom(userId, chatId);

        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED"));
    }
}
