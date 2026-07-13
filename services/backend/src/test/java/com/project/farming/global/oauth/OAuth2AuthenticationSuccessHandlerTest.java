package com.project.farming.global.oauth;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.jwtToken.CustomUserDetails;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.RefreshTokenSessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenSessionService refreshTokenSessionService;

    @Mock
    private SessionOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Mock
    private Authentication authentication;

    @Test
    void oauthLoginStoresRefreshSessionBeforeRedirect() throws Exception {
        User user = User.builder()
                .userId(7L)
                .email("oauth@example.com")
                .password("encoded")
                .nickname("oauth-user")
                .oauthProvider("GOOGLE")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
        when(authentication.getPrincipal()).thenReturn(new CustomUserDetails(user));
        when(jwtTokenProvider.generateToken(7L, 0L)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(7L, 0L)).thenReturn("refresh-token");
        when(jwtTokenProvider.getRefreshExpirationRemainingTimeMillis("refresh-token"))
                .thenReturn(60_000L);
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                jwtTokenProvider, refreshTokenSessionService, authorizationRequestRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(refreshTokenSessionService).store(eq(7L), eq("refresh-token"), any(Instant.class));
        verify(authorizationRequestRepository).clearRedirectUriCookie(request, response);
        assertThat(response.getRedirectedUrl())
                .startsWith("gardendoctor://oauth2redirect#accessToken=")
                .contains("refreshToken=refresh-token");
    }
}
