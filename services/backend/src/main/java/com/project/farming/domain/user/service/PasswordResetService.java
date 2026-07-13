package com.project.farming.domain.user.service;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.InvalidPasswordResetTokenException;
import com.project.farming.global.jwtToken.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenStore tokenStore;
    private final PasswordResetMailDispatcher mailDispatcher;

    public void requestPasswordReset(String email) {
        tokenStore.assertRequestAllowed(email);
        userRepository.findByEmail(email).ifPresent(user -> issueAndSend(user, email));
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        Long userId = tokenStore.consume(rawToken)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("유효하지 않거나 만료된 비밀번호 재설정 토큰입니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("유효하지 않거나 만료된 비밀번호 재설정 토큰입니다."));

        user.updatePassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.deleteByUserId(userId);
    }

    private void issueAndSend(User user, String email) {
        String rawToken = tokenStore.issue(user.getUserId());
        mailDispatcher.dispatch(email, rawToken, user.getUserId());
    }
}
