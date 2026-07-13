package com.project.farming.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.farming.global.jwtToken.JwtAuthenticationFilter;
import com.project.farming.global.oauth.CustomOAuth2UserService;
import com.project.farming.global.oauth.OAuth2AuthenticationSuccessHandler;
import com.project.farming.global.oauth.SessionOAuth2AuthorizationRequestRepository;
import com.project.farming.global.response.CommonResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final AdminAuthenticationSuccessHandler adminAuthenticationSuccessHandler;
    private final SessionOAuth2AuthorizationRequestRepository oAuth2AuthorizationRequestRepository;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .httpBasic(h -> h.disable())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/**", "/auth/**", "/images/**")
            )

            .authorizeHttpRequests(auth -> auth
                // --- 1) 인증 없이 허용 ---
                .requestMatchers("/auth/register").permitAll()
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/auth/token/refresh").permitAll()
                .requestMatchers("/auth/forgot-password", "/auth/password-reset/confirm").permitAll()
                .requestMatchers("/oauth2/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").access((authentication, context) ->
                        new AuthorizationDecision(environment.matchesProfiles("performance")))
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-resources/**", "/webjars/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/", "/home", "/login", "/denied", "/expired", "/css/**", "/favicon.ico").permitAll()

                // --- 2) 인증 필요 ---
                .requestMatchers("/auth/logout").authenticated()
                .requestMatchers("/auth/user/me").authenticated()
                .requestMatchers("/api/farms/**").authenticated()
                .requestMatchers("/api/plants/**").authenticated()
                .requestMatchers("/api/user-plants/**").authenticated()
                .requestMatchers("/api/diaries/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()

                // --- 3) 관리자 ---
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(a -> a.authorizationRequestRepository(oAuth2AuthorizationRequestRepository))
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2AuthenticationSuccessHandler)
            )

            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/loginProc")
                .usernameParameter("id")
                .passwordParameter("password")
                .successHandler(adminAuthenticationSuccessHandler)
                .failureUrl("/login?error=true")
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
            )

            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    String accept = request.getHeader("Accept");
                    // 웹 브라우저 요청인 경우
                    if (accept != null && accept.contains("text/html")) {
                        response.sendRedirect("/denied");
                        return;
                    }
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(objectMapper.writeValueAsString(
                            CommonResponse.error("인증이 필요합니다.", "AUTHENTICATION_REQUIRED")));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    String accept = request.getHeader("Accept");
                    if (accept != null && accept.contains("text/html")) {
                        response.sendRedirect("/denied");
                        return;
                    }
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(objectMapper.writeValueAsString(
                            CommonResponse.error("접근 권한이 없습니다.", "ACCESS_DENIED")));
                })
            )

            .sessionManagement(session -> session
                .maximumSessions(1)
                .expiredUrl("/expired")
            )

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

}
