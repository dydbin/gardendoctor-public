package com.project.farming.domain.diary.controller;

import com.project.farming.domain.diary.dto.DiaryRequest;
import com.project.farming.domain.diary.dto.DiaryResponse;
import com.project.farming.domain.diary.service.DiaryService;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.response.CursorPageResponse;
import com.project.farming.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Diary API", description = "일지(Diary) 관련 API")
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
public class DiaryController {

    private final DiaryService diaryService;

    /**
     * 새로운 일지 생성
     */
    @Operation(
            summary = "새 일지 생성",
            description = """
        새로운 일지를 생성합니다.
        - `request`: JSON 형식의 DiaryRequest
        - `imageFile`: 첨부 이미지 (선택)
        **주의:** Content-Type은 multipart/form-data로 설정하여 업로드하세요.
        request는 JSON 문자열로 전송해야 합니다.
        """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "일지 생성 성공"),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<DiaryResponse>> createDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestPart("diaryRequest") @Valid DiaryRequest diaryRequest,
            @RequestPart(value = "imageFile", required = false) MultipartFile imageFile) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }

        Long diaryId = diaryService.createDiary(
                customUserDetails.getUser(),
                diaryRequest.getTitle(),
                diaryRequest.getContent(),
                diaryRequest.getDiaryDate(),
                imageFile,
                diaryRequest.getWatered(),
                diaryRequest.getPruned(),
                diaryRequest.getFertilized(),
                diaryRequest.getSelectedUserPlantIds()
        ).getDiaryId();
        DiaryResponse response = diaryService.getDiaryById(diaryId, customUserDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success("일지 생성 성공", response));
    }

    /**
     * 특정 일지 조회
     */
    @Operation(summary = "특정 일지 조회", description = "특정 ID에 해당하는 일지의 상세 정보를 조회합니다.")
    @GetMapping("/{diaryId}")
    public ResponseEntity<CommonResponse<DiaryResponse>> getDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long diaryId) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }

        DiaryResponse response = diaryService.getDiaryById(diaryId, customUserDetails.getUser());
        return ResponseEntity.ok(CommonResponse.success("일지 조회 성공", response));
    }

    /**
     * 특정 사용자의 모든 일지 조회 (캘린더 기본 뷰 - 최신순)
     */
    @Deprecated(since = "2026-07", forRemoval = true)
    @Operation(
            summary = "특정 사용자의 모든 일지 조회 (offset, deprecated)",
            description = "호환 목적으로 2026-09-30까지 유지합니다. 신규 feed 조회는 /my-diaries/cursor를 사용해야 합니다.",
            deprecated = true
    )
    @GetMapping("/my-diaries")
    public ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> getAllMyDiaries(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PageableDefault(size = 20) Pageable pageable) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }

        PageResponse<DiaryResponse> responses = diaryService.getAllDiariesByUser(
                customUserDetails.getUser(), pageable);
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", "Wed, 30 Sep 2026 23:59:59 GMT")
                .header("Link", "</api/diaries/my-diaries/cursor>; rel=\"successor-version\"")
                .body(CommonResponse.success("일지 목록 조회 성공", responses));
    }

    @Operation(
            summary = "내 일지 cursor 목록 조회",
            description = "최신순 일지를 keyset cursor로 조회합니다. 첫 요청에서는 cursor를 생략하고, 다음 요청부터 응답의 nextCursor를 전달합니다."
    )
    @GetMapping("/my-diaries/cursor")
    public ResponseEntity<CommonResponse<CursorPageResponse<DiaryResponse>>> getMyDiaryCursorFeed(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }

        CursorPageResponse<DiaryResponse> responses = diaryService.getDiaryFeedByUser(
                customUserDetails.getUser(), cursor, size);
        return ResponseEntity.ok(CommonResponse.success("일지 cursor 목록 조회 성공", responses));
    }

    /**
     * 특정 사용자의 특정 기간 동안의 일지 조회 (캘린더 날짜별 정렬)
     */
    @Operation(summary = "특정 기간 동안의 일지 조회", description = "현재 로그인된 사용자가 작성한 일지 중 특정 기간 내의 일지 목록을 조회합니다. 날짜별 캘린더 조회에 사용됩니다.")
    @GetMapping("/my-diaries/by-date")
    public ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> getMyDiariesByDateRange(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Parameter(description = "검색 시작 날짜 (ISO 8601 형식, 예: 2024-07-01)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "검색 종료 날짜 (ISO 8601 형식, 예: 2024-07-31)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        PageResponse<DiaryResponse> responses = diaryService.getDiariesByUserAndDateRange(
                customUserDetails.getUser(), startDate, endDate, pageable);
        return ResponseEntity.ok(CommonResponse.success("일지 기간 조회 성공", responses));
    }

    /**
     * 특정 사용자 식물(UserPlant)에 연결된 일지 조회 (닉네임 기반 태그 검색)
     */
    @Operation(summary = "특정 사용자 식물(UserPlant)에 연결된 일지 조회", description = "특정 UserPlant에 연결된 현재 사용자의 모든 일지를 조회합니다. 클라이언트 드롭다운에서 특정 닉네임의 작물을 선택하여 일지를 필터링할 때 사용됩니다.")
    @GetMapping("/my-diaries/by-user-plant/{userPlantId}")
    public ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> getMyDiariesByUserPlant(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Parameter(description = "검색할 사용자 식물 ID") @PathVariable Long userPlantId,
            @PageableDefault(size = 20) Pageable pageable) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        PageResponse<DiaryResponse> responses = diaryService.getDiariesByUserAndUserPlant(
                customUserDetails.getUser(), userPlantId, pageable);
        return ResponseEntity.ok(CommonResponse.success("식물별 일지 조회 성공", responses));
    }

    /**
     * 여러 사용자 식물(UserPlant) 중 하나라도 연결된 일지 조회 (다중 태그 검색)
     */
    @Operation(summary = "여러 사용자 식물(UserPlant) 중 하나라도 연결된 일지 조회", description = "현재 사용자가 등록한 여러 UserPlant 중 하나라도 연결된 일지 목록을 조회합니다. 다중 태그 검색과 유사합니다.")
    @GetMapping("/my-diaries/by-user-plants")
    public ResponseEntity<CommonResponse<PageResponse<DiaryResponse>>> getMyDiariesByUserPlants(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @Parameter(description = "콤마로 구분된 사용자 식물 ID 목록 (예: 101,103)") @RequestParam List<Long> userPlantIds,
            @PageableDefault(size = 20) Pageable pageable) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        PageResponse<DiaryResponse> responses = diaryService.getDiariesByUserAndUserPlants(
                customUserDetails.getUser(), userPlantIds, pageable);
        return ResponseEntity.ok(CommonResponse.success("복수 식물 일지 조회 성공", responses));
    }

    /**
     * 일지 수정
     */
    @Operation(
            summary = "일지 수정",
            description = """
                특정 ID의 일지 정보를 수정합니다.
                
                - `request`: JSON 형식의 DiaryRequest (`deleteExistingImage` 포함)
                - `newImageFile`: 새 이미지 파일 (선택, 기존 이미지 교체용)
                
                **Content-Type:** multipart/form-data
                """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "일지 수정 성공"),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
                    @ApiResponse(responseCode = "401", description = "인증 실패"),
                    @ApiResponse(responseCode = "404", description = "일지를 찾을 수 없음")
            }
    )
    @PutMapping(value = "/{diaryId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommonResponse<DiaryResponse>> updateDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long diaryId,
            @RequestPart("request") @Valid DiaryRequest request,
            @RequestPart(value = "newImageFile", required = false) MultipartFile newImageFile) {

        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }

        Long updatedDiaryId = diaryService.updateDiary(
                diaryId,
                customUserDetails.getUser(),
                request.getTitle(),
                request.getContent(),
                request.getDiaryDate(),
                newImageFile,
                request.isDeleteExistingImage(),
                request.getWatered(),
                request.getPruned(),
                request.getFertilized(),
                request.getSelectedUserPlantIds()
        ).getDiaryId();
        DiaryResponse response = diaryService.getDiaryById(updatedDiaryId, customUserDetails.getUser());
        return ResponseEntity.ok(CommonResponse.success("일지 수정 성공", response));
    }

    /**
     * 일지 삭제
     */
    @Operation(summary = "일지 삭제", description = "특정 ID에 해당하는 일지를 삭제합니다. 연결된 이미지 파일도 함께 삭제됩니다.")
    @DeleteMapping("/{diaryId}")
    public ResponseEntity<CommonResponse<Void>> deleteDiary(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails customUserDetails,
            @PathVariable Long diaryId) {
        if (customUserDetails == null || customUserDetails.getUser() == null) {
            return unauthorized();
        }
        diaryService.deleteDiary(diaryId, customUserDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<CommonResponse<T>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED"));
    }
}
