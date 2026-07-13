package com.project.farming.validation;

import com.project.farming.domain.notification.dto.NoticeRequest;
import com.project.farming.domain.notification.dto.NotificationRequest;
import com.project.farming.domain.notification.outbox.FcmOutboxBulkRetryRequest;
import com.project.farming.domain.user.dto.PasswordChangeRequest;
import com.project.farming.domain.user.dto.PasswordResetConfirmRequest;
import com.project.farming.domain.user.dto.RegisterRequest;
import com.project.farming.domain.userplant.dto.UserPlantRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationContractTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void careIntervalsShouldAcceptBoundariesAndRejectNullZeroNegativeAndAboveMaximum() {
        assertThat(validator.validateValue(UserPlantRequest.class, "waterIntervalDays", 1)).isEmpty();
        assertThat(validator.validateValue(UserPlantRequest.class, "waterIntervalDays", 365)).isEmpty();
        assertThat(validator.validateValue(UserPlantRequest.class, "waterIntervalDays", null)).isNotEmpty();
        assertThat(validator.validateValue(UserPlantRequest.class, "waterIntervalDays", 0)).isNotEmpty();
        assertThat(validator.validateValue(UserPlantRequest.class, "pruneIntervalDays", -1)).isNotEmpty();
        assertThat(validator.validateValue(UserPlantRequest.class, "fertilizeIntervalDays", 366)).isNotEmpty();
    }

    @Test
    void noticeAndNotificationTextShouldMatchOneHundredAndFiveHundredCharacterColumns() {
        assertThat(validator.validateValue(NoticeRequest.class, "title", "t".repeat(100))).isEmpty();
        assertThat(validator.validateValue(NoticeRequest.class, "title", "t".repeat(101))).isNotEmpty();
        assertThat(validator.validateValue(NoticeRequest.class, "content", "c".repeat(500))).isEmpty();
        assertThat(validator.validateValue(NoticeRequest.class, "content", "c".repeat(501))).isNotEmpty();
        assertThat(validator.validateValue(NotificationRequest.class, "title", "t".repeat(101))).isNotEmpty();
        assertThat(validator.validateValue(NotificationRequest.class, "message", "m".repeat(501))).isNotEmpty();
    }

    @Test
    void notificationAndRetryIdsShouldBePositiveNonNullAndLimitedToFiveHundred() {
        NotificationRequest valid = new NotificationRequest(List.of(1L, 2L), "title", "message");
        NotificationRequest invalidElements = new NotificationRequest(
                new ArrayList<>(java.util.Arrays.asList(1L, null, 0L)), "title", "message");
        NotificationRequest tooMany = new NotificationRequest(
                java.util.stream.LongStream.rangeClosed(1, 501).boxed().toList(), "title", "message");

        assertThat(validator.validate(valid)).isEmpty();
        assertThat(validator.validate(invalidElements)).isNotEmpty();
        assertThat(validator.validate(tooMany)).isNotEmpty();
        assertThat(validator.validate(new FcmOutboxBulkRetryRequest(List.of(1L, 2L)))).isEmpty();
        assertThat(validator.validate(new FcmOutboxBulkRetryRequest(List.of(0L)))).isNotEmpty();
        assertThat(validator.validate(new FcmOutboxBulkRetryRequest(List.of()))).isNotEmpty();
    }

    @Test
    void registrationChangeAndResetShouldShareTheSamePasswordPolicy() {
        RegisterRequest register = new RegisterRequest("user@example.com", "OnlyLetters", "user");
        PasswordChangeRequest change = new PasswordChangeRequest();
        change.setCurrentPassword("Current-password1!");
        change.setNewPassword("OnlyLetters");
        PasswordResetConfirmRequest reset = new PasswordResetConfirmRequest();
        reset.setToken("token");
        reset.setNewPassword("OnlyLetters");

        assertThat(validator.validate(register)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("password");
        assertThat(validator.validate(change)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("newPassword");
        assertThat(validator.validate(reset)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("newPassword");

        assertThat(validator.validateValue(RegisterRequest.class, "password", "Valid-password1!")).isEmpty();
        assertThat(validator.validateValue(PasswordChangeRequest.class, "newPassword", "Valid-password1!")).isEmpty();
        assertThat(validator.validateValue(
                PasswordResetConfirmRequest.class, "newPassword", "Valid-password1!")).isEmpty();
    }
}
