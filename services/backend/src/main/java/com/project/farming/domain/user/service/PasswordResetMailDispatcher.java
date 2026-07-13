package com.project.farming.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetMailDispatcher {

    private final PasswordResetMailService mailService;
    private final PasswordResetTokenStore tokenStore;

    @Async("passwordResetMailExecutor")
    public void dispatch(String email, String rawToken, Long userId) {
        try {
            mailService.sendResetLink(email, rawToken);
        } catch (RuntimeException exception) {
            tokenStore.revoke(rawToken);
            log.error("Password reset email delivery failed. userId={}", userId, exception);
        }
    }
}
