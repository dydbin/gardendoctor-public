package com.project.farming.domain.user.service;

import com.project.farming.domain.user.dto.UserMyPageResponse;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.global.exception.InvalidRefreshTokenException;
import com.project.farming.global.exception.UserNotFoundException;
import com.project.farming.global.image.entity.DefaultImages;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.jwtToken.JwtBlacklistService;
import com.project.farming.global.jwtToken.JwtToken;
import com.project.farming.global.jwtToken.JwtTokenProvider;
import com.project.farming.global.jwtToken.RefreshTokenSessionService;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
import com.project.farming.global.image.service.ImageFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklistService jwtBlacklistService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final ImageFileRepository imageFileRepository;
    private final ImageFileService imageFileService;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public User registerUser(String email, String password, String nickname) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }
        // DefaultImages 클래스의 상수를 사용하여 s3Key로 엔티티를 조회합니다.
        ImageFile defaultImageFile = imageFileRepository.findByS3Key(DefaultImages.DEFAULT_USER_IMAGE)
                .orElseThrow(() -> new IllegalStateException("기본 사용자 이미지가 존재하지 않습니다."));

        User newUser = User.builder()
                .email(email)
                .nickname(nickname)
                .password(passwordEncoder.encode(password)) // 비밀번호 암호화
                .oauthProvider("LOCAL") // 자체 회원가입은 "LOCAL"로 구분
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .profileImageFileId(defaultImageFile.getImageFileId())
                .build();
        return userRepository.save(newUser);
    }

    @Transactional
    public JwtToken login(String email, String password) {
        loginAttemptService.assertAllowed(email);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );

            User user = userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("인증된 사용자를 찾을 수 없습니다."));

            String accessToken = jwtTokenProvider.generateToken(
                    user.getUserId(), user.getCredentialVersion());
            String refreshTokenString = jwtTokenProvider.generateRefreshToken(
                    user.getUserId(), user.getCredentialVersion());
            long refreshTokenExpirationMillis = jwtTokenProvider.getRefreshExpirationRemainingTimeMillis(refreshTokenString);
            Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMillis);

            refreshTokenSessionService.store(user.getUserId(), refreshTokenString, expiresAt);
            loginAttemptService.recordSuccess(email);

            return JwtToken.builder()
                    .grantType("Bearer")
                    .accessToken(accessToken)
                    .refreshToken(refreshTokenString)
                    .build();
        } catch (AuthenticationException e) {
            loginAttemptService.recordFailure(email);
            log.warn("로그인 실패: 이메일 또는 비밀번호가 잘못되었습니다.");
            throw new BadCredentialsException("이메일 또는 비밀번호가 잘못되었습니다.", e);
        }
    }

    @Transactional
    public void logout(String accessToken, Long userId) {
        long remainingTimeMillis = jwtTokenProvider.getAccessExpirationRemainingTimeMillis(accessToken);
        if (remainingTimeMillis > 0) {
            jwtBlacklistService.blacklistToken(accessToken, remainingTimeMillis);
            log.info("Access Token 블랙리스트 등록 완료. 사용자 ID: {}", userId);
        } else {
            log.warn("만료되었거나 유효하지 않은 Access Token이 로그아웃 요청에 사용되었습니다. 사용자 ID: {}", userId);
        }

        // Refresh Token 삭제
        userRepository.findById(userId).ifPresentOrElse(user -> {
            refreshTokenSessionService.revoke(userId);
            log.info("사용자 {} (ID: {})의 Refresh Token 삭제를 시도했습니다.", user.getEmail(), userId);
        }, () -> {
            log.warn("로그아웃 요청: 사용자 ID {}를 찾을 수 없습니다. 해당 사용자는 존재하지 않거나 이미 삭제되었습니다.", userId);
            throw new UserNotFoundException("로그아웃하려는 사용자를 찾을 수 없습니다.");
        });
    }
    /**
     * 사용자의 FCM 토큰을 업데이트합니다.
     * @param userId 사용자 ID
     * @param fcmToken 새로운 FCM 토큰
     */
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("FCM 토큰을 업데이트할 사용자를 찾을 수 없습니다."));

        user.updateFcmToken(fcmToken);
        log.info("사용자 ID {}의 FCM 토큰이 업데이트되었습니다.", userId);
    }

    @Transactional
    public JwtToken refreshTokens(String refreshTokenString) {
        if (!jwtTokenProvider.validateRefreshToken(refreshTokenString)) {
            throw new InvalidRefreshTokenException("유효하지 않은 리프레시 토큰입니다.");
        }

        // JWT에서 직접 userId (Long)를 추출
        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(refreshTokenString);

        if (jwtBlacklistService.isBlacklisted(refreshTokenString)) {
            log.warn("블랙리스트에 등록된 리프레시 토큰이 재사용되었습니다. 사용자 ID {}의 모든 리프레시 토큰을 삭제합니다.", userId);
            if (userRepository.existsById(userId)) {
                refreshTokenSessionService.revoke(userId);
            }
            throw new InvalidRefreshTokenException("비정상적인 접근입니다. 재로그인이 필요합니다.");
        }

        // userId (Long)로 User 객체 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRefreshTokenException("사용자를 찾을 수 없습니다. 재로그인이 필요합니다."));
        if (!jwtTokenProvider.matchesRefreshCredentialVersion(
                refreshTokenString, user.getCredentialVersion())) {
            refreshTokenSessionService.revoke(userId);
            throw new InvalidRefreshTokenException("비밀번호 변경으로 세션이 만료되었습니다. 다시 로그인해주세요.");
        }

        String newAccessToken = jwtTokenProvider.generateToken(userId, user.getCredentialVersion());
        String newRefreshTokenString = jwtTokenProvider.generateRefreshToken(
                userId, user.getCredentialVersion());
        long newRefreshTokenExpirationMillis = jwtTokenProvider.getRefreshExpirationRemainingTimeMillis(newRefreshTokenString);
        Instant newRefreshTokenExpiresAt = Instant.now().plusMillis(newRefreshTokenExpirationMillis);

        int rotatedRows = refreshTokenSessionService.rotate(
                userId,
                refreshTokenString,
                newRefreshTokenString,
                newRefreshTokenExpiresAt
        );

        if (rotatedRows != 1) {
            log.warn("리프레시 토큰 rotation 충돌 또는 재사용이 감지되었습니다. 사용자 ID: {}", userId);
            throw new InvalidRefreshTokenException("DB에 존재하지 않는 리프레시 토큰입니다. 재로그인이 필요합니다.");
        }

        long oldRefreshTokenRemainingTime = jwtTokenProvider.getRefreshExpirationRemainingTimeMillis(refreshTokenString);
        if (oldRefreshTokenRemainingTime > 0) {
            jwtBlacklistService.blacklistToken(refreshTokenString, oldRefreshTokenRemainingTime);
            log.info("이전 Refresh Token 블랙리스트 등록 완료. 사용자 ID: {}", userId);
        } else {
            log.warn("이미 만료된 Refresh Token이 재발급 요청에 사용되었습니다. 사용자 ID: {}", userId);
            throw new InvalidRefreshTokenException("만료된 Refresh Token입니다. 재로그인이 필요합니다.");
        }

        log.info("토큰 재발급 성공. 새로운 Access Token 및 Refresh Token 발급.");

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenString)
                .build();
    }
    @Transactional(readOnly = true)
    public UserMyPageResponse getMyPageInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("유저가 없습니다."));

        return UserMyPageResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(resolveProfileImageUrl(user.getProfileImageFileId()))
                .oauthProvider(user.getOauthProvider())
                .role(user.getRole().name())
                .subscriptionStatus(user.getSubscriptionStatus())
                .build();
    }

    // 내 정보 삭제
    @Transactional
    public void deleteMyPageInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("삭제할 회원이 없습니다."));

        refreshTokenSessionService.revoke(userId);
        userRepository.delete(user);
    }


    /**
     * 사용자의 닉네임을 변경합니다.
     *
     * @param userId   사용자 ID
     * @param newNickname 새로운 닉네임
     * @return 업데이트된 사용자 정보 응답
     * @throws UserNotFoundException 사용자를 찾을 수 없을 때
     */
    @Transactional
    public UserMyPageResponse updateNickname(Long userId, String newNickname) {
        if (newNickname == null || newNickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임은 비어있을 수 없습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        user.updateNickname(newNickname);
        log.info("사용자 ID {}의 닉네임이 {}로 변경되었습니다.", userId, newNickname);

        // 변경된 사용자 정보를 응답으로 변환하여 반환
        return UserMyPageResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(resolveProfileImageUrl(user.getProfileImageFileId()))
                .oauthProvider(user.getOauthProvider())
                .role(user.getRole().name())
                .subscriptionStatus(user.getSubscriptionStatus())
                .build();
    }
    @Transactional
    public UserMyPageResponse updateProfileImage(Long userId, MultipartFile imageFile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        if (imageFile == null || imageFile.isEmpty()) {
            throw new IllegalArgumentException("업로드된 이미지 파일이 없습니다.");
        }

        Long currentImageFileId = user.getProfileImageFileId();

        ImageFile newProfileImage = imageFileService.uploadImage(imageFile, ImageDomainType.USER, userId);

        if (currentImageFileId != null) {
            imageFileRepository.findById(currentImageFileId)
                    .filter(currentImage -> !DefaultImages.isDefaultImage(currentImage.getS3Key()))
                    .ifPresent(currentImage -> imageFileService.deleteImage(currentImage.getImageFileId()));
        }

        user.updateProfileImageFile(newProfileImage.getImageFileId());

        return toMyPageResponse(user);
    }

    @Transactional
    public UserMyPageResponse deleteProfileImage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        ImageFile defaultImageFile = imageFileRepository.findByS3Key(DefaultImages.DEFAULT_USER_IMAGE)
                .orElseThrow(() -> new IllegalStateException("기본 사용자 이미지가 존재하지 않습니다."));

        Long currentImageFileId = user.getProfileImageFileId();
        if (currentImageFileId != null) {
            imageFileRepository.findById(currentImageFileId)
                    .filter(currentImage -> !DefaultImages.isDefaultImage(currentImage.getS3Key()))
                    .ifPresent(currentImage -> {
                        imageFileService.deleteImage(currentImage.getImageFileId());
                        log.info("사용자 ID {}의 기존 프로필 이미지가 삭제되었습니다. 이미지 ID: {}",
                                userId, currentImage.getImageFileId());
                    });
        }

        // 사용자 엔티티의 프로필 이미지를 기본 이미지로 업데이트
        user.updateProfileImageFile(defaultImageFile.getImageFileId());

        // 응답으로 변환하여 반환
        return toMyPageResponse(user);
    }

    private UserMyPageResponse toMyPageResponse(User user) {
        return UserMyPageResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .profileImageUrl(resolveProfileImageUrl(user.getProfileImageFileId()))
                .oauthProvider(user.getOauthProvider())
                .role(user.getRole().name())
                .subscriptionStatus(user.getSubscriptionStatus())
                .build();
    }

    private String resolveProfileImageUrl(Long profileImageFileId) {
        if (profileImageFileId == null) {
            return null;
        }
        return imageFileRepository.findById(profileImageFileId)
                .map(ImageFile::getImageUrl)
                .orElse(null);
    }
    /**
     * 로그인된 사용자의 비밀번호를 변경합니다.
     * @param userId 현재 로그인된 사용자의 ID
     * @param currentPassword 현재 비밀번호
     * @param newPassword 새 비밀번호
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));

        // 1. 현재 비밀번호가 맞는지 확인
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 2. 새 비밀번호로 업데이트 (암호화)
        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenSessionService.revoke(userId);

        log.info("사용자 ID {}의 비밀번호가 변경되었습니다.", userId);
    }

}
