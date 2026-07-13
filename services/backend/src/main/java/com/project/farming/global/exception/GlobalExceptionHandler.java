// src/main/java/com/project/farming/global/exception/GlobalExceptionHandler.java
package com.project.farming.global.exception;

import com.project.farming.global.response.CommonResponse;
import com.project.farming.global.ai.AiRemoteServiceException;
import com.project.farming.global.ai.AiServiceTimeoutException;
import com.project.farming.global.ai.AiServiceUnavailableException;
import com.project.farming.domain.notification.outbox.NoticeDeliveryInProgressException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException; // Spring Security의 UsernameNotFoundException
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice // 모든 @Controller, @RestController에서 발생하는 예외를 처리
@Slf4j
public class GlobalExceptionHandler {

    /**
     * @Valid 어노테이션으로 인한 유효성 검증 실패 시 발생하는 예외를 처리합니다.
     * @param ex MethodArgumentNotValidException
     * @return BAD_REQUEST (400) 응답과 유효성 검증 오류 상세 정보
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        log.warn("Validation failed: {}", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CommonResponse.error("유효성 검증 실패", "VALIDATION_ERROR", errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<CommonResponse<Map<String, String>>> handleBindingExceptions(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("Request binding validation failed: {}", errors);
        return ResponseEntity.badRequest()
                .body(CommonResponse.error("유효성 검증 실패", "VALIDATION_ERROR", errors));
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<CommonResponse<Void>> handleConstraintViolation(RuntimeException ex) {
        log.warn("Request constraint validation failed: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                CommonResponse.error("유효성 검증 실패", "VALIDATION_ERROR")
        );
    }

    /**
     * IllegalArgumentException (예: 유효하지 않은 인자 값) 예외를 처리합니다.
     * @param ex IllegalArgumentException
     * @return BAD_REQUEST (400) 응답과 예외 메시지
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                CommonResponse.error(ex.getMessage(), "BAD_REQUEST")
        );
    }

    @ExceptionHandler(NoticeDeliveryInProgressException.class)
    public ResponseEntity<CommonResponse<Void>> handleNoticeDeliveryInProgressException(
            NoticeDeliveryInProgressException ex) {
        log.warn("Notice delivery is in progress: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                CommonResponse.error(ex.getMessage(), "NOTICE_DELIVERY_IN_PROGRESS")
        );
    }

    /**
     * Refresh Token 검증, 재사용, 만료, rotation 충돌 실패를 처리합니다.
     * @param ex InvalidRefreshTokenException
     * @return UNAUTHORIZED (401) 응답과 재로그인 안내 메시지
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<CommonResponse<Void>> handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        log.warn("InvalidRefreshTokenException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                CommonResponse.error(ex.getMessage(), "INVALID_REFRESH_TOKEN")
        );
    }

    /**
     * 커스텀 예외: NotificationNotFoundException (알림을 찾을 수 없을 때) 처리
     * @param ex NotificationNotFoundException
     * @return NOT_FOUND (404) 응답과 예외 메시지
     */
    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleNotificationNotFoundException(NotificationNotFoundException ex) {
        log.warn("NotificationNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                CommonResponse.error(ex.getMessage(), "NOTIFICATION_NOT_FOUND")
        );
    }

    /**
     * 커스텀 예외: UserNotFoundException (사용자를 찾을 수 없을 때) 처리
     * @param ex UserNotFoundException
     * @return NOT_FOUND (404) 응답과 예외 메시지
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<CommonResponse<Void>> handleUserNotFoundException(UserNotFoundException ex) {
        log.warn("UserNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                CommonResponse.error(ex.getMessage(), "USER_NOT_FOUND")
        );
    }

    /**
     * @ResponseStatus만 붙어 있던 도메인 not-found 예외가 generic 500 handler에 묻히지 않도록 처리합니다.
     */
    @ExceptionHandler({
            NotFoundException.class,
            ImageFileNotFoundException.class,
            UserPlantNotFoundException.class,
            PlantNotFoundException.class,
            FarmNotFoundException.class,
            NoticeNotFoundException.class,
            NoSuchElementException.class
    })
    public ResponseEntity<CommonResponse<Void>> handleResourceNotFoundException(RuntimeException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                CommonResponse.error(ex.getMessage(), "RESOURCE_NOT_FOUND")
        );
    }

    /**
     * 커스텀 예외: AccessDeniedException (권한이 없을 때) 처리
     * @param ex AccessDeniedException
     * @return FORBIDDEN (403) 응답과 예외 메시지
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CommonResponse<Void>> handleCustomAccessDeniedException(AccessDeniedException ex) {
        log.warn("CustomAccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                CommonResponse.error(ex.getMessage(), "ACCESS_DENIED")
        );
    }

    /**
     * 커스텀 예외: CustomException (일반적인 비즈니스 로직 오류) 처리
     * @param ex CustomException
     * @return BAD_REQUEST (400) 응답과 예외 메시지
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonResponse<Void>> handleCustomException(CustomException ex) {
        log.warn("CustomException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                CommonResponse.error(ex.getMessage(), "CUSTOM_ERROR")
        );
    }

    /**
     * 커스텀 예외: AiAnalysisException (AI 분석 실패 등 서버 내부 오류) 처리
     * @param ex AiAnalysisException
     * @return INTERNAL_SERVER_ERROR (500) 응답과 예외 메시지
     */
    @ExceptionHandler(AiAnalysisException.class)
    public ResponseEntity<CommonResponse<Void>> handleAiAnalysisException(AiAnalysisException ex) {
        log.error("AiAnalysisException: {}", ex.getMessage(), ex); // AI 분석 오류는 상세 로깅
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CommonResponse.error("AI 분석 중 오류가 발생했습니다: " + ex.getMessage(), "AI_ANALYSIS_ERROR")
        );
    }

    /**
     * 커스텀 예외: UsernameException (사용자 이름 관련 유효성 또는 충돌 오류) 처리
     * @param ex UsernameException
     * @return BAD_REQUEST (400) 응답과 예외 메시지
     */
    @ExceptionHandler(UsernameException.class)
    public ResponseEntity<CommonResponse<Void>> handleUsernameException(UsernameException ex) {
        log.warn("UsernameException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                CommonResponse.error(ex.getMessage(), "USERNAME_ERROR")
        );
    }

    /**
     * Spring Security의 인증 관련 예외 (예: UsernameNotFoundException, BadCredentialsException 등)를 처리합니다.
     * @param ex AuthenticationException
     * @return UNAUTHORIZED (401) 응답과 인증 실패 메시지
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CommonResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("AuthenticationException: {}", ex.getMessage());
        String errorMessage = "인증에 실패했습니다."; // 기본 메시지

        if (ex instanceof UsernameNotFoundException) {
            errorMessage = "이메일 또는 비밀번호가 잘못되었습니다."; // 사용자에게 구체적인 정보 노출 자제
        }
        // 다른 AuthenticationException 유형에 따라 메시지를 다르게 설정할 수 있습니다.
        // 예: if (ex instanceof BadCredentialsException) { errorMessage = "비밀번호가 일치하지 않습니다."; }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                CommonResponse.error(errorMessage, "AUTHENTICATION_FAILED")
        );
    }

    @ExceptionHandler(LoginRateLimitExceededException.class)
    public ResponseEntity<CommonResponse<Void>> handleLoginRateLimitExceededException(LoginRateLimitExceededException ex) {
        log.warn("Login rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                CommonResponse.error(ex.getMessage(), "LOGIN_RATE_LIMITED")
        );
    }

    @ExceptionHandler(PasswordResetRateLimitExceededException.class)
    public ResponseEntity<CommonResponse<Void>> handlePasswordResetRateLimitExceededException(
            PasswordResetRateLimitExceededException ex) {
        log.warn("Password reset rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                CommonResponse.error(ex.getMessage(), "PASSWORD_RESET_RATE_LIMITED")
        );
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    public ResponseEntity<CommonResponse<Void>> handleInvalidPasswordResetTokenException(
            InvalidPasswordResetTokenException ex) {
        log.warn("Invalid password reset token: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                CommonResponse.error(ex.getMessage(), "INVALID_PASSWORD_RESET_TOKEN")
        );
    }

    @ExceptionHandler(PasswordResetUnavailableException.class)
    public ResponseEntity<CommonResponse<Void>> handlePasswordResetUnavailableException(
            PasswordResetUnavailableException ex) {
        log.error("Password reset storage unavailable", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                CommonResponse.error("비밀번호 재설정 기능을 일시적으로 사용할 수 없습니다.", "PASSWORD_RESET_UNAVAILABLE")
        );
    }

    @ExceptionHandler(AiServiceTimeoutException.class)
    public ResponseEntity<CommonResponse<Void>> handleAiServiceTimeoutException(AiServiceTimeoutException ex) {
        log.warn("AI service timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(
                CommonResponse.error("AI 서버 응답 시간이 초과되었습니다.", "AI_SERVICE_TIMEOUT")
        );
    }

    @ExceptionHandler({AiServiceUnavailableException.class, AiRemoteServiceException.class})
    public ResponseEntity<CommonResponse<Void>> handleAiServiceUnavailableException(RuntimeException ex) {
        log.warn("AI service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                CommonResponse.error("AI 서버 요청을 처리할 수 없습니다.", "AI_SERVICE_UNAVAILABLE")
        );
    }

    @ExceptionHandler(PhotoAnalysisRateLimitExceededException.class)
    public ResponseEntity<CommonResponse<Void>> handlePhotoAnalysisRateLimitExceededException(
            PhotoAnalysisRateLimitExceededException ex) {
        log.warn("Photo analysis rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                CommonResponse.error(ex.getMessage(), "PHOTO_ANALYSIS_RATE_LIMITED")
        );
    }

    /**
     * 같은 데이터를 동시에 수정해 JPA 버전 충돌이 발생한 경우를 처리합니다.
     * @param ex optimistic locking exception
     * @return CONFLICT (409) 응답과 재조회/재시도 안내 메시지
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<CommonResponse<Void>> handleOptimisticLockException(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                CommonResponse.error("동시에 수정된 데이터입니다. 최신 데이터를 다시 조회한 뒤 다시 시도해주세요.", "OPTIMISTIC_LOCK_CONFLICT")
        );
    }

    /**
     * DB row lock timeout이나 deadlock으로 비관적 락을 얻지 못한 경우를 처리합니다.
     * @param ex pessimistic locking exception
     * @return CONFLICT (409) 응답과 재시도 안내 메시지
     */
    @ExceptionHandler({
            PessimisticLockingFailureException.class,
            PessimisticLockException.class,
            LockTimeoutException.class
    })
    public ResponseEntity<CommonResponse<Void>> handlePessimisticLockException(Exception ex) {
        log.warn("Pessimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                CommonResponse.error("다른 작업이 처리 중입니다. 잠시 후 다시 시도해주세요.", "PESSIMISTIC_LOCK_CONFLICT")
        );
    }

    /**
     * 그 외 모든 예상치 못한 예외를 처리합니다. (가장 일반적인 예외 핸들러)
     * 이 핸들러는 위에 정의된 특정 예외 핸들러들이 처리하지 못한 모든 RuntimeException을 포함합니다.
     * @param ex Exception
     * @return INTERNAL_SERVER_ERROR (500) 응답과 일반적인 서버 오류 메시지
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<Void>> handleAllExceptions(Exception ex) {
        log.error("Unhandled exception occurred: {}", ex.getMessage(), ex); // 스택 트레이스 포함 상세 로깅
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CommonResponse.error("서버 오류가 발생했습니다.", "INTERNAL_SERVER_ERROR")
        );
    }
}
