package com.project.farming.domain.user.repository;

import com.project.farming.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
    Optional<User> findByEmail(String email);
    Boolean existsByEmail(String email); // AuthService.registerUser에서 사용
    @Query("""
        SELECT u.userId AS userId,
               u.email AS email,
               u.nickname AS nickname,
               u.oauthProvider AS oauthProvider,
               u.oauthId AS oauthId,
               u.role AS role,
               u.fcmToken AS fcmToken,
               u.subscriptionStatus AS subscriptionStatus,
               image.imageUrl AS profileImageUrl
        FROM User u
        LEFT JOIN ImageFile image ON image.imageFileId = u.profileImageFileId
        ORDER BY u.userId ASC
        """)
    Page<UserAdminResponseRow> findAllAdminResponseRowsByOrderByUserIdAsc(Pageable pageable);

    @Query("""
        SELECT u.userId AS userId,
               u.email AS email,
               u.nickname AS nickname,
               u.oauthProvider AS oauthProvider,
               u.oauthId AS oauthId,
               u.role AS role,
               u.fcmToken AS fcmToken,
               u.subscriptionStatus AS subscriptionStatus,
               image.imageUrl AS profileImageUrl
        FROM User u
        LEFT JOIN ImageFile image ON image.imageFileId = u.profileImageFileId
        WHERE u.nickname LIKE :keyword ESCAPE '!'
        ORDER BY u.nickname ASC, u.userId ASC
        """)
    Page<UserAdminResponseRow> findAdminResponseRowsByNicknameOrderByNicknameAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT u.userId AS userId,
               u.email AS email,
               u.nickname AS nickname,
               u.oauthProvider AS oauthProvider,
               u.oauthId AS oauthId,
               u.role AS role,
               u.fcmToken AS fcmToken,
               u.subscriptionStatus AS subscriptionStatus,
               image.imageUrl AS profileImageUrl
        FROM User u
        LEFT JOIN ImageFile image ON image.imageFileId = u.profileImageFileId
        WHERE u.email LIKE :keyword ESCAPE '!'
        ORDER BY u.email ASC, u.userId ASC
        """)
    Page<UserAdminResponseRow> findAdminResponseRowsByEmailOrderByEmailAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query("""
        SELECT u.userId AS userId,
               u.email AS email,
               u.nickname AS nickname,
               u.oauthProvider AS oauthProvider,
               u.oauthId AS oauthId,
               u.role AS role,
               u.fcmToken AS fcmToken,
               u.subscriptionStatus AS subscriptionStatus,
               image.imageUrl AS profileImageUrl
        FROM User u
        LEFT JOIN ImageFile image ON image.imageFileId = u.profileImageFileId
        WHERE u.userId = :userId
        """)
    Optional<UserAdminResponseRow> findAdminResponseRowByUserId(@Param("userId") Long userId);


    @Query("SELECT COALESCE(MAX(u.userId), 0) FROM User u")
    Long findMaxActiveUserId();

    @Query("SELECT u FROM User u WHERE u.fcmToken IS NOT NULL AND TRIM(u.fcmToken) <> ''")
    List<User> findUsersByFcmToken();

    @Query("SELECT u.fcmToken FROM User u WHERE u.fcmToken IS NOT NULL AND TRIM(u.fcmToken) <> ''")
    List<String> findFcmTokens();
}
