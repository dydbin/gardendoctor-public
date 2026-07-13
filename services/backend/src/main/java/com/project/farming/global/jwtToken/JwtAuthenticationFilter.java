package com.project.farming.global.jwtToken;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklistService jwtBlacklistService;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        String token = null;

        // Authorization 헤더에서 "Bearer " 접두사를 제거하고 토큰을 추출합니다.
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        // 토큰이 존재하고, 유효하며, 블랙리스트에 없는 경우에만 인증을 진행합니다.
        if (token != null && jwtTokenProvider.validateAccessToken(token) && !jwtBlacklistService.isBlacklisted(token)) {
            Long userId = jwtTokenProvider.getUserIdFromAccessToken(token);

            // 추출된 userId를 String으로 변환하여 CustomUserDetailsService를 통해 UserDetails를 로드합니다.
            // UserDetails에는 사용자의 권한 정보가 포함됩니다.
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(String.valueOf(userId));
            if (userDetails instanceof CustomUserDetails customUserDetails
                    && !jwtTokenProvider.matchesAccessCredentialVersion(
                            token, customUserDetails.getUser().getCredentialVersion())) {
                filterChain.doFilter(request, response);
                return;
            }

            // UserDetails와 권한 정보를 사용하여 UsernamePasswordAuthenticationToken을 생성합니다.
            // 두 번째 인자는 자격 증명(비밀번호)이지만, JWT 인증에서는 null로 설정합니다.
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            // 요청의 세부 정보를 인증 객체에 설정합니다. (예: IP 주소, 세션 ID 등)
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 현재 스레드의 SecurityContext에 인증 객체를 설정합니다.
            // 이렇게 하면 이후 요청 처리 과정에서 인증된 사용자 정보를 사용할 수 있습니다.
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 다음 필터로 요청을 전달합니다.
        filterChain.doFilter(request, response);
    }
}
