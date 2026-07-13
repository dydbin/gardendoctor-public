package com.project.farming.global.exception;

import com.project.farming.global.ai.AiServiceTimeoutException;
import com.project.farming.global.ai.AiServiceUnavailableException;
import com.project.farming.domain.userplant.dto.UserPlantRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new OptimisticLockTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void optimisticLockFailureShouldReturnConflict() throws Exception {
        mockMvc.perform(put("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void pessimisticLockFailureShouldReturnRetryableConflict() throws Exception {
        mockMvc.perform(put("/test/pessimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PESSIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void invalidRefreshTokenShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put("/test/invalid-refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void authenticationFailureShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put("/test/authentication-failure"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void loginRateLimitShouldReturnTooManyRequests() throws Exception {
        mockMvc.perform(put("/test/login-rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    void passwordResetRateLimitShouldReturnTooManyRequests() throws Exception {
        mockMvc.perform(put("/test/password-reset-rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_RESET_RATE_LIMITED"));
    }

    @Test
    void invalidPasswordResetTokenShouldReturnBadRequest() throws Exception {
        mockMvc.perform(put("/test/invalid-password-reset-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    @Test
    void unavailablePasswordResetStoreShouldReturnServiceUnavailable() throws Exception {
        mockMvc.perform(put("/test/password-reset-unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_RESET_UNAVAILABLE"));
    }

    @Test
    void aiTimeoutShouldReturnGatewayTimeout() throws Exception {
        mockMvc.perform(put("/test/ai-timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.errorCode").value("AI_SERVICE_TIMEOUT"));
    }

    @Test
    void aiConnectionFailureShouldReturnBadGateway() throws Exception {
        mockMvc.perform(put("/test/ai-unavailable"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("AI_SERVICE_UNAVAILABLE"));
    }

    @Test
    void photoAnalysisRateLimitShouldReturnTooManyRequests() throws Exception {
        mockMvc.perform(put("/test/photo-analysis-rate-limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("PHOTO_ANALYSIS_RATE_LIMITED"));
    }

    @Test
    void domainNotFoundShouldReturnNotFound() throws Exception {
        mockMvc.perform(put("/test/domain-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void noSuchElementShouldReturnNotFound() throws Exception {
        mockMvc.perform(put("/test/no-such-element"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void accessDeniedShouldReturnForbidden() throws Exception {
        mockMvc.perform(put("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void imageNotFoundShouldReturnNotFound() throws Exception {
        mockMvc.perform(put("/test/image-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidCareIntervalShouldReturnValidationBadRequestBeforeDatabaseAccess() throws Exception {
        mockMvc.perform(post("/test/user-plant-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plantName": "몬스테라",
                                  "plantNickname": "초록이",
                                  "gardenUniqueId": 1,
                                  "isNotificationEnabled": true,
                                  "waterIntervalDays": 0,
                                  "pruneIntervalDays": 7,
                                  "fertilizeIntervalDays": 30,
                                  "watered": false,
                                  "pruned": false,
                                  "fertilized": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.waterIntervalDays").exists());
    }

    @RestController
    private static class OptimisticLockTestController {

        @PutMapping("/test/optimistic-lock")
        void optimisticLock() {
            throw new ObjectOptimisticLockingFailureException("Diary", 1L);
        }

        @PutMapping("/test/pessimistic-lock")
        void pessimisticLock() {
            throw new CannotAcquireLockException("catalog row is locked");
        }

        @PutMapping("/test/invalid-refresh-token")
        void invalidRefreshToken() {
            throw new InvalidRefreshTokenException("재로그인이 필요합니다.");
        }

        @PutMapping("/test/authentication-failure")
        void authenticationFailure() {
            throw new BadCredentialsException("이메일 또는 비밀번호가 잘못되었습니다.");
        }

        @PutMapping("/test/login-rate-limit")
        void loginRateLimit() {
            throw new LoginRateLimitExceededException("로그인 실패가 반복되어 잠시 후 다시 시도해주세요.");
        }

        @PutMapping("/test/password-reset-rate-limit")
        void passwordResetRateLimit() {
            throw new PasswordResetRateLimitExceededException("비밀번호 재설정 요청이 반복되었습니다.");
        }

        @PutMapping("/test/invalid-password-reset-token")
        void invalidPasswordResetToken() {
            throw new InvalidPasswordResetTokenException("유효하지 않거나 만료된 토큰입니다.");
        }

        @PutMapping("/test/password-reset-unavailable")
        void passwordResetUnavailable() {
            throw new PasswordResetUnavailableException("일시적으로 사용할 수 없습니다.", new IllegalStateException());
        }

        @PutMapping("/test/ai-timeout")
        void aiTimeout() {
            throw new AiServiceTimeoutException("timeout", new IllegalStateException());
        }

        @PutMapping("/test/ai-unavailable")
        void aiUnavailable() {
            throw new AiServiceUnavailableException("unavailable", new IllegalStateException());
        }

        @PutMapping("/test/photo-analysis-rate-limit")
        void photoAnalysisRateLimit() {
            throw new PhotoAnalysisRateLimitExceededException("잠시 후 다시 시도해주세요.");
        }

        @PutMapping("/test/domain-not-found")
        void domainNotFound() {
            throw new PlantNotFoundException("해당 식물이 존재하지 않습니다.");
        }

        @PutMapping("/test/no-such-element")
        void noSuchElement() {
            throw new NoSuchElementException("해당 ID의 일지를 찾을 수 없습니다.");
        }

        @PutMapping("/test/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("본인 프로필 이미지만 업로드할 수 있습니다.");
        }

        @PutMapping("/test/image-not-found")
        void imageNotFound() {
            throw new ImageFileNotFoundException("존재하지 않는 이미지 파일입니다.");
        }

        @PostMapping("/test/user-plant-validation")
        void validateUserPlant(@Valid @RequestBody UserPlantRequest request) {
        }
    }
}
