package com.project.farming.global.oauth;

import com.project.farming.domain.user.entity.User;
import com.project.farming.global.jwtToken.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthUserService oauthUserService;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        // 개인정보 노출 방지를 위해 debug 레벨 권장
        log.debug("OAuth2 User Attributes: {}", oAuth2User.getAttributes());

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        String oauthId = null;
        String email = null;
        String nickname = null;

        if ("google".equals(registrationId)) {
            oauthId = oAuth2User.getName();
            email = oAuth2User.getAttribute("email");
            nickname = oAuth2User.getAttribute("name");
        } else if ("kakao".equals(registrationId)) {
            Object kakaoId = oAuth2User.getAttribute("id");
            oauthId = kakaoId != null ? String.valueOf(kakaoId) : null;

            Object kakaoAccountAttribute = oAuth2User.getAttributes().get("kakao_account");
            if (kakaoAccountAttribute instanceof Map<?, ?> kakaoAccount) {
                boolean hasEmail = Boolean.TRUE.equals(kakaoAccount.get("has_email"));
                boolean needsAgree = Boolean.TRUE.equals(kakaoAccount.get("email_needs_agreement"));
                if (hasEmail && !needsAgree) {
                    email = stringValue(kakaoAccount.get("email"));
                }
                if (kakaoAccount.get("profile") instanceof Map<?, ?> profile) {
                    nickname = stringValue(profile.get("nickname"));
                }
            }
        } else if ("naver".equals(registrationId)) {
            Object responseAttribute = oAuth2User.getAttributes().get("response");
            if (responseAttribute instanceof Map<?, ?> attributes) {
                oauthId = stringValue(attributes.get("id"));
                email = stringValue(attributes.get("email"));
                nickname = stringValue(attributes.get("nickname"));
            } else {
                log.warn("Naver OAuth2 response attributes are missing.");
            }
        } else {
            throw new OAuth2AuthenticationException("지원하지 않는 OAuth2 제공자입니다.");
        }

        if (email == null || email.trim().isEmpty()) {
            // 정책: 이메일 필수. (카카오 콘솔에서 account_email 동의 필수/재동의 설정 권장)
            throw new OAuth2AuthenticationException("이메일 정보는 필수입니다. 이메일 제공에 동의해주세요.");
        }
        if (nickname == null || nickname.isBlank()) {
            nickname = registrationId + "_" + (oauthId != null ? oauthId : "user");
        }

        User user = oauthUserService.saveOrUpdateUserFromOAuth(oauthId, email, nickname, registrationId);
        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }
}
