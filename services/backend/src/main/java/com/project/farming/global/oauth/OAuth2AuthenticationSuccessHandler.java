package com.project.farming.global.oauth;

import com.project.farming.domain.user.entity.User;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.RefreshTokenSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final SessionOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    // 허용 스킴/호스트 화이트리스트
    private static final Set<String> ALLOWED_SCHEMES = Set.of("https", "http", "gardendoctor");
    private static final Set<String> ALLOWED_WEB_HOSTS = Set.of("localhost", "127.0.0.1" /*, "your-web-domain.com" */);

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        // 1) 사용자 식별 (CustomUserDetails 권장)
        User user;
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            user = cud.getUser();
        } else {
            log.error("Unexpected principal type: {}", authentication.getPrincipal().getClass());
            getRedirectStrategy().sendRedirect(request, response, "/login-error");
            return;
        }

        // 2) 토큰 생성 (로그에 토큰 노출 금지!)
        String accessToken = jwtTokenProvider.generateToken(
                user.getUserId(), user.getCredentialVersion());
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getUserId(), user.getCredentialVersion());
        long refreshTokenTtl = jwtTokenProvider.getRefreshExpirationRemainingTimeMillis(refreshToken);
        if (refreshTokenTtl <= 0) {
            throw new IllegalStateException("발급된 OAuth refresh token의 만료 시간이 유효하지 않습니다.");
        }
        refreshTokenSessionService.store(
                user.getUserId(), refreshToken, Instant.now().plusMillis(refreshTokenTtl));
        log.info("OAuth2 Login Success: userId = {}", user.getUserId());

        // 3) redirect_uri를 쿠키에서 복구
        String redirectUri = CookieUtils.getCookie(request,
                SessionOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(c -> c.getValue())
                .orElse(null);

        // 4) 안전한 리다이렉트 URL 생성 (기본값은 앱 딥링크)
        String target = buildSafeRedirect(redirectUri, accessToken, refreshToken);

        // 5) 쿠키 정리 + 리다이렉트
        authorizationRequestRepository.clearRedirectUriCookie(request, response);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String buildSafeRedirect(String redirectUri, String accessToken, String refreshToken) {
        String fallback = "gardendoctor://oauth2redirect"; // Flutter deep-link fallback
        String safe = (redirectUri != null && isAllowed(redirectUri)) ? redirectUri : fallback;

        String at = URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
        String rt = URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);

        // 토큰은 URL 프래그먼트(#)로 전달 → 쿼리스트링보다 노출 가능성 낮음
        return safe + "#accessToken=" + at + "&refreshToken=" + rt;
    }

    private boolean isAllowed(String uri) {
        try {
            var u = java.net.URI.create(uri);
            if (!ALLOWED_SCHEMES.contains(u.getScheme())) return false;
            if ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) {
                return u.getHost() != null && ALLOWED_WEB_HOSTS.contains(u.getHost());
            }
            // 커스텀 스킴(gardendoctor)은 호스트가 없을 수 있음
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
