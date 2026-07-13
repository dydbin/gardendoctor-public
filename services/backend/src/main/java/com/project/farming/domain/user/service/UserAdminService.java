package com.project.farming.domain.user.service;

import com.project.farming.domain.user.command.UserAdminCommand;
import com.project.farming.domain.user.dto.UserAdminResponse;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.domain.user.repository.UserAdminResponseRow;
import com.project.farming.global.exception.UserNotFoundException;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.service.ImageFileService;
import com.project.farming.global.pagination.PageRequestPolicy;
import com.project.farming.global.search.SearchKeywordPattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final ImageFileService imageFileService;

    /**
     * 전체 사용자 목록 조회(별명순)
     *
     * @return 각 사용자 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public Page<UserAdminResponse> findAllUsers(Pageable pageable) {
        Page<UserAdminResponseRow> userList = userRepository.findAllAdminResponseRowsByOrderByUserIdAsc(
                PageRequestPolicy.stable(pageable));
        if (userList.isEmpty()) {
            log.info("등록된 사용자가 없습니다.");
        }
        return userList.map(this::toUserAdminResponse);
    }

    /**
     * 사용자 목록 검색(별명/이메일 순)
     * - 사용자의 별명 또는 이메일로 검색
     *
     * @param searchType 검색 조건(name 또는 email) - 기본값은 name
     * @param keyword 검색어(별명 또는 이메일)
     * @return 검색된 사용자 정보의 응답 리스트
     */
    @Transactional(readOnly = true)
    public Page<UserAdminResponse> findUsersByKeyword(
            String searchType, String keyword, Pageable pageable) {
        String likeKeyword = SearchKeywordPattern.prefix(keyword);
        Pageable stablePageable = PageRequestPolicy.stable(pageable);
        Page<UserAdminResponseRow> foundUsers = switch (searchType) {
            case "name" -> userRepository.findAdminResponseRowsByNicknameOrderByNicknameAsc(
                    likeKeyword, stablePageable);
            case "email" -> userRepository.findAdminResponseRowsByEmailOrderByEmailAsc(
                    likeKeyword, stablePageable);
            default -> {
                log.error("지원하지 않는 검색 조건입니다: {}", searchType);
                throw new IllegalArgumentException("지원하지 않는 검색 조건입니다: " + searchType);
            }
        };
        return foundUsers.map(this::toUserAdminResponse);
    }

    /**
     * 특정 사용자 조회
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자 정보 응답
     */
    @Transactional(readOnly = true)
    public UserAdminResponse findUser(Long userId) {
        UserAdminResponseRow user = userRepository.findAdminResponseRowByUserId(userId)
                .orElseThrow(() -> {
                    log.error("사용자를 찾을 수 없습니다: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId);
                });
        return toUserAdminResponse(user);
    }

    /**
     * 특정 사용자 정보 수정
     *
     * @param userId 수정할 사용자 ID
     * @param command 새로 저장할 사용자 정보
     * @param newFile 새로 업로드할 프로필 이미지 파일 (선택적)
     */
    @Transactional
    public void updateUser(Long userId, UserAdminCommand command, MultipartFile newFile) {
        User user = findUserById(userId);
        if (newFile != null && !newFile.isEmpty()) {
            // 새로운 이미지 파일이 첨부되어 있는 경우
            ImageFile imageFile = user.getProfileImageFileId() == null
                    ? imageFileService.uploadImage(newFile, ImageDomainType.USER, userId)
                    : imageFileService.updateImage(
                            user.getProfileImageFileId(), // 기존 이미지 파일
                            newFile, ImageDomainType.USER, userId);
            user.updateProfileImageFile(imageFile.getImageFileId());
        }
        user.updateEmail(command.email());
        user.updateNickname(command.nickname());
        user.setOauthProvider(command.oauthProvider());
        user.setOauthId(command.oauthId());
        user.updateRole(UserRole.valueOf(command.role()));
        user.updateFcmToken(command.fcmToken());
        user.updateSubscriptionStatus(command.subscriptionStatus());
        userRepository.save(user);
    }

    /**
     * 특정 사용자 삭제
     *
     * @param userId 삭제할 사용자의 ID
     */
    @Transactional
    public void deleteUser(Long userId) {
        authService.deleteMyPageInfo(userId);
    }

    private UserAdminResponse toUserAdminResponse(UserAdminResponseRow user) {
        return UserAdminResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .oauthProvider(user.getOauthProvider())
                .oauthId(user.getOauthId())
                .role(user.getRole().name())
                .fcmToken(user.getFcmToken())
                .subscriptionStatus(user.getSubscriptionStatus())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }

    /**
     * ID로 사용자 정보 조회
     *
     * @param userId 조회할 사용자 정보의 ID
     * @return 조회한 사용자 정보
     */
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("사용자를 찾을 수 없습니다: {}", userId);
                    return new UserNotFoundException("사용자를 찾을 수 없습니다: " + userId);
                });
    }

    /**
     * NoticeService에서 사용
     * - 모든 사용자의 FCM 토큰 리스트 반환
     *
     * @return 모든 사용자의 FCM 토큰 리스트
     */
    public List<String> getUserFcmTokenList() {
        List<String> fcmTokens = userRepository.findFcmTokens();
        if (fcmTokens.isEmpty()) {
            log.error("FCM 토큰이 저장된 사용자가 존재하지 않습니다.");
            throw new UserNotFoundException("FCM 토큰이 저장된 사용자가 존재하지 않습니다.");
        }
        return fcmTokens;
    }
}
