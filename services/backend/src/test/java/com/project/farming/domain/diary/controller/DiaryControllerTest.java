package com.project.farming.domain.diary.controller;

import com.project.farming.domain.diary.dto.DiaryRequest;
import com.project.farming.domain.diary.dto.DiaryResponse;
import com.project.farming.domain.diary.entity.Diary;
import com.project.farming.domain.diary.service.DiaryService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.CursorPageResponse;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryControllerTest {

    @Mock
    private DiaryService diaryService;

    private DiaryController diaryController;

    @BeforeEach
    void setUp() {
        diaryController = new DiaryController(diaryService);
    }

    @Test
    void getDiaryShouldReturnCommonUnauthorizedBodyWhenPrincipalMissing() {
        ResponseEntity<CommonResponse<DiaryResponse>> response = diaryController.getDiary(null, 1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(response.getBody().getMessage()).isEqualTo("인증이 필요합니다.");
        verifyNoInteractions(diaryService);
    }

    @Test
    void createDiaryShouldWrapCreatedResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        DiaryRequest request = diaryRequest();
        Diary createdDiary = diary(10L);
        DiaryResponse diaryResponse = diaryResponse(10L);

        when(diaryService.createDiary(
                eq(userDetails.getUser()),
                eq(request.getTitle()),
                eq(request.getContent()),
                eq(request.getDiaryDate()),
                isNull(),
                eq(request.getWatered()),
                eq(request.getPruned()),
                eq(request.getFertilized()),
                eq(request.getSelectedUserPlantIds())
        )).thenReturn(createdDiary);
        when(diaryService.getDiaryById(10L, userDetails.getUser())).thenReturn(diaryResponse);

        ResponseEntity<CommonResponse<DiaryResponse>> response =
                diaryController.createDiary(userDetails, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("일지 생성 성공");
        assertThat(response.getBody().getData()).isSameAs(diaryResponse);
    }

    @Test
    void getDiaryShouldWrapDetailInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        DiaryResponse diaryResponse = diaryResponse(10L);
        when(diaryService.getDiaryById(10L, userDetails.getUser())).thenReturn(diaryResponse);

        ResponseEntity<CommonResponse<DiaryResponse>> response = diaryController.getDiary(userDetails, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("일지 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(diaryResponse);
    }

    @Test
    @SuppressWarnings("removal")
    void getAllMyDiariesShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<DiaryResponse> diaries = page(diaryResponse(10L));
        when(diaryService.getAllDiariesByUser(userDetails.getUser(), pageable)).thenReturn(diaries);

        ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> response =
                diaryController.getAllMyDiaries(userDetails, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("일지 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(diaries);
        assertThat(response.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(response.getHeaders().getFirst("Sunset"))
                .isEqualTo("Wed, 30 Sep 2026 23:59:59 GMT");
        assertThat(response.getHeaders().getFirst("Link"))
                .isEqualTo("</api/diaries/my-diaries/cursor>; rel=\"successor-version\"");
    }

    @Test
    void getDiaryCursorFeedShouldWrapCursorMetadataWithoutOffsetTotals() {
        CustomUserDetails userDetails = userDetails();
        CursorPageResponse<DiaryResponse> diaries = new CursorPageResponse<>(
                List.of(diaryResponse(10L)), 20, true, "next-token");
        when(diaryService.getDiaryFeedByUser(userDetails.getUser(), "cursor-token", 20))
                .thenReturn(diaries);

        ResponseEntity<CommonResponse<CursorPageResponse<DiaryResponse>>> response =
                diaryController.getMyDiaryCursorFeed(userDetails, "cursor-token", 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("일지 cursor 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(diaries);
    }

    @Test
    void getMyDiariesByDateRangeShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        LocalDate startDate = LocalDate.of(2026, 7, 1);
        LocalDate endDate = LocalDate.of(2026, 7, 10);
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<DiaryResponse> diaries = page(diaryResponse(10L));
        when(diaryService.getDiariesByUserAndDateRange(userDetails.getUser(), startDate, endDate, pageable))
                .thenReturn(diaries);

        ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> response =
                diaryController.getMyDiariesByDateRange(userDetails, startDate, endDate, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("일지 기간 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(diaries);
    }

    @Test
    void getMyDiariesByUserPlantShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<DiaryResponse> diaries = page(diaryResponse(10L));
        when(diaryService.getDiariesByUserAndUserPlant(userDetails.getUser(), 20L, pageable)).thenReturn(diaries);

        ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> response =
                diaryController.getMyDiariesByUserPlant(userDetails, 20L, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("식물별 일지 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(diaries);
    }

    @Test
    void getMyDiariesByUserPlantsShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        List<Long> userPlantIds = List.of(20L, 21L);
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<DiaryResponse> diaries = page(diaryResponse(10L));
        when(diaryService.getDiariesByUserAndUserPlants(userDetails.getUser(), userPlantIds, pageable))
                .thenReturn(diaries);

        ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> response =
                diaryController.getMyDiariesByUserPlants(userDetails, userPlantIds, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("복수 식물 일지 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(diaries);
    }

    @Test
    void updateDiaryShouldWrapResponseInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        DiaryRequest request = diaryRequest();
        Diary updatedDiary = diary(10L);
        DiaryResponse diaryResponse = diaryResponse(10L);

        when(diaryService.updateDiary(
                eq(10L),
                eq(userDetails.getUser()),
                eq(request.getTitle()),
                eq(request.getContent()),
                eq(request.getDiaryDate()),
                isNull(),
                eq(request.isDeleteExistingImage()),
                eq(request.getWatered()),
                eq(request.getPruned()),
                eq(request.getFertilized()),
                eq(request.getSelectedUserPlantIds())
        )).thenReturn(updatedDiary);
        when(diaryService.getDiaryById(10L, userDetails.getUser())).thenReturn(diaryResponse);

        ResponseEntity<CommonResponse<DiaryResponse>> response =
                diaryController.updateDiary(userDetails, 10L, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("일지 수정 성공");
        assertThat(response.getBody().getData()).isSameAs(diaryResponse);
    }

    @Test
    void deleteDiaryShouldKeepNoContentSuccessResponse() {
        CustomUserDetails userDetails = userDetails();

        ResponseEntity<CommonResponse<Void>> response = diaryController.deleteDiary(userDetails, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(diaryService).deleteDiary(10L, userDetails.getUser());
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

    private DiaryRequest diaryRequest() {
        return DiaryRequest.builder()
                .title("일지 제목")
                .content("일지 내용")
                .diaryDate(LocalDate.of(2026, 7, 10))
                .watered(true)
                .pruned(false)
                .fertilized(true)
                .selectedUserPlantIds(List.of(20L))
                .deleteExistingImage(false)
                .build();
    }

    private PageResponse<DiaryResponse> page(DiaryResponse diary) {
        return PageResponse.of(List.of(diary), 0, 20, false, 1);
    }

    private Diary diary(Long diaryId) {
        return Diary.builder()
                .diaryId(diaryId)
                .userId(1L)
                .title("일지 제목")
                .content("일지 내용")
                .diaryDate(LocalDate.of(2026, 7, 10))
                .watered(true)
                .pruned(false)
                .fertilized(true)
                .build();
    }

    private DiaryResponse diaryResponse(Long diaryId) {
        return new DiaryResponse(
                diaryId,
                1L,
                "일지 제목",
                "일지 내용",
                LocalDate.of(2026, 7, 10),
                "https://example.com/diary.jpg",
                true,
                false,
                true,
                LocalDateTime.of(2026, 7, 10, 10, 0),
                LocalDateTime.of(2026, 7, 10, 11, 0),
                List.of(20L)
        );
    }
}
