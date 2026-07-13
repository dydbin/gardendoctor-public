package com.project.farming.global.oauth;

import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUserService {

    private final UserRepository userRepository;
    private final ImageFileService imageFileService;
    private final PasswordEncoder passwordEncoder;

    /**
     * OAuth2 로그인 정보를 기반으로 사용자 정보를 저장하거나 업데이트합니다.
     */
    @Transactional
    public User saveOrUpdateUserFromOAuth(String oauthId, String email, String nickname, String oauthProvider) {
        User user = userRepository.findByOauthProviderAndOauthId(oauthProvider, oauthId).orElse(null);

        if (user != null) {
            // 기존 OAuth 사용자: 닉네임만 업데이트(프로필 이미지는 유지)
            user.updateNickname(nickname);
            log.info("Existing OAuth user updated: Email={}, Provider={}, OAuthId={}", email, oauthProvider, oauthId);
            return userRepository.save(user);
        }

        // 이메일로 기존 계정 찾기 → 연동
        if (email != null) {
            User byEmail = userRepository.findByEmail(email).orElse(null);
            if (byEmail != null) {
                if (byEmail.getOauthProvider() == null || byEmail.getOauthProvider().isEmpty()) {
                    byEmail.setOauthProvider(oauthProvider);
                    byEmail.setOauthId(oauthId);
                    byEmail.updateNickname(nickname);

                    // 기존 프로필 이미지가 없을 때만 기본 이미지 설정
                    if (byEmail.getProfileImageFileId() == null) setDefaultProfileImage(byEmail);

                    log.info("Existing normal user linked with OAuth: Email={}, Provider={}, OAuthId={}", email, oauthProvider, oauthId);
                    return userRepository.save(byEmail);
                } else if (!byEmail.getOauthProvider().equals(oauthProvider)) {
                    log.error("Email conflict during OAuth login: Email {} already exists with other Provider {}", email, byEmail.getOauthProvider());
                    throw new OAuth2AuthenticationException("이미 존재하는 이메일입니다. 다른 방식으로 로그인해주세요.");
                } else {
                    log.error("Unexpected duplicate OAuth user detected: Email={}, Provider={}, OAuthId={}", email, oauthProvider, oauthId);
                    throw new OAuth2AuthenticationException("알 수 없는 오류로 이미 등록된 계정입니다.");
                }
            }
        }

        // 신규 사용자 생성
        String generatedPassword = UUID.randomUUID().toString();
        User created = User.builder()
                .email(email)
                .password(passwordEncoder.encode(generatedPassword))
                .nickname(nickname)
                .oauthProvider(oauthProvider)
                .oauthId(oauthId)
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();

        created = userRepository.save(created);

        // 프로필 이미지 없으면 기본 이미지 설정
        if (created.getProfileImageFileId() == null) setDefaultProfileImage(created);

        log.info("New OAuth user created: Email={}, Provider={}, OAuthId={}", email, oauthProvider, oauthId);
        return userRepository.save(created);
    }

    /**
     * 사용자의 프로필 이미지를 DefaultImages.DEFAULT_USER_IMAGE로 설정합니다.
     * 기본 이미지는 애플리케이션 시작 시 DB에 저장되어 있어야 합니다.
     */
    private void setDefaultProfileImage(User user) {
        Optional<ImageFile> defaultImageOptional = imageFileService.getImageFileByS3Key(DefaultImages.DEFAULT_USER_IMAGE);
        if (defaultImageOptional.isPresent()) {
            user.updateProfileImageFile(defaultImageOptional.get().getImageFileId());
            log.debug("사용자 {}의 프로필 이미지를 기본 이미지({})로 설정했습니다.", user.getEmail(), DefaultImages.DEFAULT_USER_IMAGE);
        } else {
            log.warn("기본 사용자 프로필 이미지 ({})를 DB에서 찾을 수 없습니다. 사용자 {}에게 프로필 이미지가 설정되지 않습니다.",
                    DefaultImages.DEFAULT_USER_IMAGE, user.getEmail());
        }
    }
}
