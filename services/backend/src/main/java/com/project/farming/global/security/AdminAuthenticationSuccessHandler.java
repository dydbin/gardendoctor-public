package com.project.farming.global.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class AdminAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final RequestCache requestCache = new HttpSessionRequestCache();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        HttpSession session = request.getSession();
        String userId = authentication.getName();
        log.info("로그인된 사용자: {}", userId);

        if (!isAdmin) {
            log.info("관리자 계정이 아닙니다: {}", userId);

            // 비관리자면 세션 무효화 + 강제 로그아웃 처리
            session.invalidate();
            SecurityContextHolder.clearContext();

            // 로그인 페이지로 리다이렉트 (에러 메시지 전달)
            response.sendRedirect("/login?not_admin=true");
            return;
        }

        // 세션 유지 시간 설정
        session.setMaxInactiveInterval(60 * 20); // 초 단위

        // 세션에 사용자 등록
        session.setAttribute("userId", userId);

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
