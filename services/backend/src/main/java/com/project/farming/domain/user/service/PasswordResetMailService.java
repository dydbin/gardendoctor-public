package com.project.farming.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    @Value("${app.auth.password-reset.confirm-url}")
    private String confirmUrl;

    @Value("${app.auth.password-reset.token-ttl-seconds:900}")
    private long tokenTtlSeconds;

    public void sendResetLink(String email, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[텃밭닥터] 비밀번호 재설정 안내");
        message.setText("아래 링크에서 비밀번호를 재설정해주세요.\n\n"
                + confirmUrl + "?token=" + rawToken + "\n\n"
                + "이 링크는 " + Math.max(1, tokenTtlSeconds / 60) + "분 동안 한 번만 사용할 수 있습니다.");
        mailSender.send(message);
    }
}
