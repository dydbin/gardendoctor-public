package com.project.farming.domain.userplant.controller;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.userplant.command.UserPlantCommand;
import com.project.farming.domain.userplant.dto.UserPlantDetailResponse;
import com.project.farming.domain.userplant.dto.UserPlantListResponse;
import com.project.farming.domain.userplant.dto.UserPlantRequest;
import com.project.farming.domain.userplant.service.UserPlantService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlantControllerTest {

    @Mock
    private UserPlantService userPlantService;

    private UserPlantController userPlantController;

    @BeforeEach
    void setUp() {
        userPlantController = new UserPlantController(userPlantService);
    }

    @Test
    void createUserPlantShouldWrapCreatedDetailInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        UserPlantRequest request = request();
        UserPlantDetailResponse detail = detailResponse();
        when(userPlantService.saveUserPlant(eq(1L), any(UserPlantCommand.class), eq(null))).thenReturn(detail);

        ResponseEntity<CommonResponse<UserPlantDetailResponse>> response =
                userPlantController.createUserPlant(userDetails, request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("사용자 식물 등록 성공");
        assertThat(response.getBody().getData()).isSameAs(detail);
    }

    @Test
    void getAllUserPlantsShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<UserPlantListResponse> plants = page(listResponse());
        when(userPlantService.findAllUserPlants(1L, pageable)).thenReturn(plants);

        ResponseEntity<CommonResponse<PageResponse<UserPlantListResponse>>> response =
                userPlantController.getAllUserPlants(userDetails, pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("사용자 식물 목록 조회 성공");
        assertThat(response.getBody().getData()).isSameAs(plants);
    }

    @Test
    void searchUserPlantsShouldWrapPageInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        Pageable pageable = PageRequest.of(0, 20);
        PageResponse<UserPlantListResponse> plants = page(listResponse());
        when(userPlantService.findUserPlantsByKeyword(1L, "토마토", pageable)).thenReturn(plants);

        ResponseEntity<CommonResponse<PageResponse<UserPlantListResponse>>> response =
                userPlantController.searchUserPlants(userDetails, "토마토", pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(plants);
    }

    @Test
    void getUserPlantShouldWrapDetailInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        UserPlantDetailResponse detail = detailResponse();
        when(userPlantService.findUserPlant(1L, 10L)).thenReturn(detail);

        ResponseEntity<CommonResponse<UserPlantDetailResponse>> response =
                userPlantController.getUserPlant(userDetails, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(detail);
    }

    @Test
    void updateUserPlantShouldWrapDetailInCommonResponse() {
        CustomUserDetails userDetails = userDetails();
        UserPlantDetailResponse detail = detailResponse();
        when(userPlantService.updateUserPlant(eq(1L), eq(10L), any(UserPlantCommand.class), eq(null)))
                .thenReturn(detail);

        ResponseEntity<CommonResponse<UserPlantDetailResponse>> response =
                userPlantController.updateUserPlant(userDetails, 10L, request(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("사용자 식물 수정 성공");
        assertThat(response.getBody().getData()).isSameAs(detail);
    }

    @Test
    void deleteUserPlantShouldKeepNoContentSuccessResponse() {
        CustomUserDetails userDetails = userDetails();

        ResponseEntity<CommonResponse<Void>> response = userPlantController.deleteUserPlant(userDetails, 10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(userPlantService).deleteUserPlant(1L, 10L);
    }

    @Test
    void missingPrincipalShouldReturnCommonUnauthorizedBody() {
        ResponseEntity<CommonResponse<PageResponse<UserPlantListResponse>>> response =
                userPlantController.getAllUserPlants(null, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        verifyNoInteractions(userPlantService);
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

    private UserPlantRequest request() {
        UserPlantRequest request = new UserPlantRequest();
        request.setPlantName("토마토");
        request.setPlantNickname("베란다 토마토");
        request.setGardenUniqueId(100);
        request.setPlantingPlace("베란다");
        request.setPlantedDate(LocalDateTime.of(2026, 7, 10, 10, 0));
        request.setNotes("메모");
        request.setIsNotificationEnabled(true);
        request.setWaterIntervalDays(3);
        request.setPruneIntervalDays(7);
        request.setFertilizeIntervalDays(14);
        request.setWatered(false);
        request.setPruned(false);
        request.setFertilized(false);
        return request;
    }

    private UserPlantListResponse listResponse() {
        return UserPlantListResponse.builder()
                .userPlantId(10L)
                .plantName("토마토")
                .plantNickname("베란다 토마토")
                .plantingPlace("베란다")
                .isNotificationEnabled(true)
                .waterIntervalDays(3)
                .pruneIntervalDays(7)
                .fertilizeIntervalDays(14)
                .userPlantImageUrl("https://example.com/user-plant.jpg")
                .build();
    }

    private UserPlantDetailResponse detailResponse() {
        return UserPlantDetailResponse.builder()
                .userPlantId(10L)
                .plantName("토마토")
                .plantNickname("베란다 토마토")
                .plantingPlace("베란다")
                .plantedDate(LocalDateTime.of(2026, 7, 10, 10, 0))
                .notes("메모")
                .isNotificationEnabled(true)
                .waterIntervalDays(3)
                .pruneIntervalDays(7)
                .fertilizeIntervalDays(14)
                .watered(false)
                .pruned(false)
                .fertilized(false)
                .userPlantImageUrl("https://example.com/user-plant.jpg")
                .plantEnglishName("Tomato")
                .species("채소")
                .season("봄")
                .plantImageUrl("https://example.com/plant.jpg")
                .build();
    }

    private PageResponse<UserPlantListResponse> page(UserPlantListResponse plant) {
        return PageResponse.of(List.of(plant), 0, 20, false, 1);
    }
}
