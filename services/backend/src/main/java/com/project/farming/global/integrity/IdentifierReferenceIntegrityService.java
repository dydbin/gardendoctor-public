package com.project.farming.global.integrity;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IdentifierReferenceIntegrityService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public IdentifierReferenceIntegrityReport inspect() {
        return new IdentifierReferenceIntegrityReport(findOrphans());
    }

    @Transactional(readOnly = true)
    public List<IdentifierReferenceOrphan> findOrphans() {
        return referenceChecks().stream()
                .map(check -> new IdentifierReferenceOrphan(check.referenceName(), count(check.sql())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countOrphansByReference() {
        return findOrphans().stream()
                .collect(Collectors.toMap(
                        IdentifierReferenceOrphan::referenceName,
                        IdentifierReferenceOrphan::orphanCount));
    }

    public List<String> referenceNames() {
        return referenceChecks().stream()
                .map(ReferenceCheck::referenceName)
                .toList();
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private List<ReferenceCheck> referenceChecks() {
        return List.of(
                check("users.profile_image_file_id", """
                        SELECT COUNT(*)
                        FROM users u
                        LEFT JOIN image_files image ON image.image_file_id = u.profile_image_file_id
                        WHERE u.profile_image_file_id IS NOT NULL
                          AND image.image_file_id IS NULL
                        """),
                check("plant_info.plant_image_file_id", """
                        SELECT COUNT(*)
                        FROM plant_info plant
                        LEFT JOIN image_files image ON image.image_file_id = plant.plant_image_file_id
                        WHERE plant.plant_image_file_id IS NOT NULL
                          AND image.image_file_id IS NULL
                        """),
                check("farm_info.farm_image_file_id", """
                        SELECT COUNT(*)
                        FROM farm_info farm
                        LEFT JOIN image_files image ON image.image_file_id = farm.farm_image_file_id
                        WHERE farm.farm_image_file_id IS NOT NULL
                          AND image.image_file_id IS NULL
                        """),
                check("user_plants.user_id", """
                        SELECT COUNT(*)
                        FROM user_plants up
                        LEFT JOIN users u ON u.user_id = up.user_id
                        WHERE u.user_id IS NULL
                        """),
                check("user_plants.plant_id", """
                        SELECT COUNT(*)
                        FROM user_plants up
                        LEFT JOIN plant_info plant ON plant.plant_id = up.plant_id
                        WHERE plant.plant_id IS NULL
                        """),
                check("user_plants.farm_id", """
                        SELECT COUNT(*)
                        FROM user_plants up
                        LEFT JOIN farm_info farm ON farm.farm_id = up.farm_id
                        WHERE farm.farm_id IS NULL
                        """),
                check("user_plants.active_plant_id", """
                        SELECT COUNT(*)
                        FROM user_plants up
                        JOIN plant_info plant ON plant.plant_id = up.plant_id
                        WHERE up.deleted = false
                          AND plant.deleted = true
                        """),
                check("user_plants.active_farm_id", """
                        SELECT COUNT(*)
                        FROM user_plants up
                        JOIN farm_info farm ON farm.farm_id = up.farm_id
                        WHERE up.deleted = false
                          AND farm.deleted = true
                        """),
                check("user_plants.user_plant_image_file_id", """
                        SELECT COUNT(*)
                        FROM user_plants up
                        LEFT JOIN image_files image ON image.image_file_id = up.user_plant_image_file_id
                        WHERE up.user_plant_image_file_id IS NOT NULL
                          AND image.image_file_id IS NULL
                        """),
                check("diaries.user_id", """
                        SELECT COUNT(*)
                        FROM diaries diary
                        LEFT JOIN users u ON u.user_id = diary.user_id
                        WHERE u.user_id IS NULL
                        """),
                check("diaries.dairy_image_file_id", """
                        SELECT COUNT(*)
                        FROM diaries diary
                        LEFT JOIN image_files image ON image.image_file_id = diary.dairy_image_file_id
                        WHERE diary.dairy_image_file_id IS NOT NULL
                          AND image.image_file_id IS NULL
                        """),
                check("diary_user_plant.diary_id", """
                        SELECT COUNT(*)
                        FROM diary_user_plant link
                        LEFT JOIN diaries diary ON diary.diary_id = link.diary_id
                        WHERE diary.diary_id IS NULL
                        """),
                check("diary_user_plant.user_plant_id", """
                        SELECT COUNT(*)
                        FROM diary_user_plant link
                        LEFT JOIN user_plants up ON up.user_plant_id = link.user_plant_id
                        WHERE up.user_plant_id IS NULL
                        """),
                check("notification.user_id", """
                        SELECT COUNT(*)
                        FROM notification notification
                        LEFT JOIN users u ON u.user_id = notification.user_id
                        WHERE u.user_id IS NULL
                        """),
                check("notification.notice_id", """
                        SELECT COUNT(*)
                        FROM notification notification
                        LEFT JOIN notices notice ON notice.notice_id = notification.notice_id
                        WHERE notification.notice_id IS NOT NULL
                          AND notice.notice_id IS NULL
                        """),
                check("fcm_outbox.notice_id", """
                        SELECT COUNT(*)
                        FROM fcm_outbox outbox
                        LEFT JOIN notices notice ON notice.notice_id = outbox.notice_id
                        WHERE outbox.notice_id IS NOT NULL
                          AND notice.notice_id IS NULL
                        """),
                check("fcm_outbox.source_id", """
                        SELECT COUNT(*)
                        FROM fcm_outbox outbox
                        LEFT JOIN notices notice
                          ON outbox.source_type = 'NOTICE'
                         AND notice.notice_id = outbox.source_id
                        LEFT JOIN notification notification
                          ON outbox.source_type = 'NOTIFICATION'
                         AND notification.notification_id = outbox.source_id
                        WHERE (outbox.source_type = 'NOTICE' AND notice.notice_id IS NULL)
                           OR (outbox.source_type = 'NOTIFICATION' AND notification.notification_id IS NULL)
                        """),
                check("fcm_outbox.user_id", """
                        SELECT COUNT(*)
                        FROM fcm_outbox outbox
                        LEFT JOIN users u ON u.user_id = outbox.user_id
                        WHERE outbox.user_id IS NULL
                           OR u.user_id IS NULL
                        """),
                check("chat.user_id", """
                        SELECT COUNT(*)
                        FROM chat chat
                        LEFT JOIN users u ON u.user_id = chat.user_id
                        WHERE u.user_id IS NULL
                        """),
                check("photo_analysis.user_id", """
                        SELECT COUNT(*)
                        FROM photo_analysis analysis
                        LEFT JOIN users u ON u.user_id = analysis.user_id
                        WHERE u.user_id IS NULL
                        """),
                check("photo_analysis.photo_image_file_id", """
                        SELECT COUNT(*)
                        FROM photo_analysis analysis
                        LEFT JOIN image_files image ON image.image_file_id = analysis.photo_image_file_id
                        WHERE analysis.photo_image_file_id IS NOT NULL
                          AND image.image_file_id IS NULL
                        """),
                check("refresh_token.user_pk", """
                        SELECT COUNT(*)
                        FROM refresh_token token
                        LEFT JOIN users u ON u.user_id = token.user_pk
                        WHERE u.user_id IS NULL
                        """),
                check("image_files.USER.domain_id", imageDomainSql("USER", "users", "user_id")),
                check("image_files.PLANT.domain_id", imageDomainSql("PLANT", "plant_info", "plant_id")),
                check("image_files.DIARY.domain_id", imageDomainSql("DIARY", "diaries", "diary_id")),
                check("image_files.FARM.domain_id", imageDomainSql("FARM", "farm_info", "farm_id")),
                check("image_files.USERPLANT.domain_id", imageDomainSql("USERPLANT", "user_plants", "user_plant_id")),
                check("image_files.PHOTO.domain_id", imageDomainSql("PHOTO", "users", "user_id"))
        );
    }

    private String imageDomainSql(String domainType, String targetTable, String targetColumn) {
        return """
                SELECT COUNT(*)
                FROM image_files image
                LEFT JOIN %s target ON target.%s = image.domain_id
                WHERE image.domain_type = '%s'
                  AND image.domain_id > 0
                  AND target.%s IS NULL
                """.formatted(targetTable, targetColumn, domainType, targetColumn);
    }

    private ReferenceCheck check(String referenceName, String sql) {
        return new ReferenceCheck(referenceName, sql);
    }

    private record ReferenceCheck(String referenceName, String sql) {
    }
}
