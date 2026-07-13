package com.project.farming.global.oauth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SessionOAuth2AuthorizationRequestRepositoryTest {

    private final SessionOAuth2AuthorizationRequestRepository repository =
            new SessionOAuth2AuthorizationRequestRepository();

    @Test
    void shouldStoreAuthorizationRequestInSessionAndRedirectUriInCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addParameter("redirect_uri", "gardendoctor://oauth2redirect");
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();

        repository.saveAuthorizationRequest(authorizationRequest, request, response);
        request.addParameter("state", "state");

        assertThat(repository.loadAuthorizationRequest(request)).isEqualTo(authorizationRequest);
        Cookie redirectCookie = response.getCookie(
                SessionOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME);
        assertThat(redirectCookie).isNotNull();
        assertThat(redirectCookie.getValue()).isEqualTo("gardendoctor://oauth2redirect");
        assertThat(redirectCookie.isHttpOnly()).isTrue();
        assertThat(redirectCookie.getMaxAge()).isEqualTo(180);
    }

    @Test
    void shouldRemoveAuthorizationRequestFromSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest();
        repository.saveAuthorizationRequest(authorizationRequest, request, response);
        request.addParameter("state", "state");

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(request, response);

        assertThat(removed).isEqualTo(authorizationRequest);
        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void shouldExpireRedirectUriCookieWhenAuthorizationRequestIsCleared() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setCookies(new Cookie(
                SessionOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME,
                "gardendoctor://oauth2redirect"));

        repository.saveAuthorizationRequest(null, request, response);

        Cookie expiredCookie = response.getCookie(
                SessionOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME);
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getMaxAge()).isZero();
        assertThat(expiredCookie.getValue()).isEmpty();
    }

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://provider.example/authorize")
                .clientId("client-id")
                .redirectUri("http://localhost/login/oauth2/code/provider")
                .state("state")
                .build();
    }
}
