package com.project.farming.integration;

import com.project.farming.domain.notification.command.NoticeCommand;
import com.project.farming.domain.notification.entity.Notice;
import com.project.farming.domain.notification.outbox.FcmOutboxBatchStore;
import com.project.farming.domain.notification.outbox.FcmOutboxDispatch;
import com.project.farming.domain.notification.repository.NoticeRepository;
import com.project.farming.domain.notification.service.NoticeService;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class NoticeNotificationIdentityIntegrationDiagnosticsTest {

    private static final int RECIPIENT_COUNT = 2;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private NoticeService noticeService;

    @Autowired
    private FcmOutboxBatchStore fcmOutboxBatchStore;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void deletingEditedNoticeShouldDeleteItsNotificationsByStableIdentity() {
        persistRecipients();
        Notice notice = persistNotice("점검 공지", "점검 전 내용");

        noticeService.sendNotice(notice.getNoticeId());
        assertThat(notificationCountForNotice(notice.getNoticeId())).isEqualTo(RECIPIENT_COUNT);

        noticeService.updateNotice(
                notice.getNoticeId(),
                new NoticeCommand("수정된 점검 공지", "점검 후 내용"));
        noticeService.deleteNotice(notice.getNoticeId());
        entityManager.flush();

        assertThat(notificationCountForNotice(notice.getNoticeId()))
                .as("Notice lifecycle must not depend on mutable title/content values.")
                .isZero();
        assertThat(outboxCountForNotice(notice.getNoticeId())).isZero();
    }

    @Test
    void noticesWithSameContentShouldHaveIndependentRecipientRows() {
        persistRecipients();
        Notice first = persistNotice("동일 문구", "동일 본문");
        Notice second = persistNotice("동일 문구", "동일 본문");

        noticeService.sendNotice(first.getNoticeId());
        noticeService.sendNotice(second.getNoticeId());
        entityManager.flush();

        assertThat(notificationCountForNotice(first.getNoticeId(), second.getNoticeId()))
                .as("Mutable content is not a Notice identifier; each Notice must own recipient rows.")
                .isEqualTo(RECIPIENT_COUNT * 2L);
    }

    @Test
    void noticeShouldSeparateInAppRecipientsFromPushRecipientsAndRefreshTokenBeforeClaim() {
        User tokenUser = persistRecipient("token-user", "old-token", "ACTIVE");
        persistRecipient("in-app-only", null, "ACTIVE");
        persistRecipient("withdrawn", "withdrawn-token", "WITHDRAWN");
        Notice notice = persistNotice("수신자 정책", "인앱과 푸시 분리");

        noticeService.sendNotice(notice.getNoticeId());
        entityManager.flush();

        assertThat(notificationCountForNotice(notice.getNoticeId())).isEqualTo(2);
        assertThat(outboxCountForNotice(notice.getNoticeId())).isEqualTo(1);
        assertThat(outboxUserId(notice.getNoticeId())).isEqualTo(tokenUser.getUserId());

        jdbcTemplate.update(
                "UPDATE users SET fcm_token = 'rotated-token' WHERE user_id = ?",
                tokenUser.getUserId());
        entityManager.clear();

        List<FcmOutboxDispatch> dispatches = fcmOutboxBatchStore.claimDueBatch(10);

        assertThat(dispatches).singleElement().satisfies(dispatch -> {
            assertThat(dispatch.userId()).isEqualTo(tokenUser.getUserId());
            assertThat(dispatch.targetToken()).isEqualTo("rotated-token");
            assertThat(dispatch.eventId())
                    .isEqualTo("notice:" + notice.getNoticeId() + ":user:" + tokenUser.getUserId());
        });
    }

    @Test
    void noticeOutboxShouldBeCancelledWhenRecipientWithdrawsBeforeClaim() {
        User user = persistRecipient("withdraw-before-claim", "active-token", "ACTIVE");
        Notice notice = persistNotice("탈퇴 재검증", "발송 직전 수신 자격 확인");
        noticeService.sendNotice(notice.getNoticeId());
        entityManager.flush();

        jdbcTemplate.update(
                "UPDATE users SET subscription_status = 'WITHDRAWN' WHERE user_id = ?",
                user.getUserId());
        entityManager.clear();

        List<FcmOutboxDispatch> dispatches = fcmOutboxBatchStore.claimDueBatch(10);

        assertThat(dispatches).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM fcm_outbox WHERE notice_id = ? AND user_id = ?",
                String.class,
                notice.getNoticeId(),
                user.getUserId())).isEqualTo("CANCELLED");
    }

    private void persistRecipients() {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        for (int index = 0; index < RECIPIENT_COUNT; index++) {
            persistRecipient(
                    "recipient-" + suffix + "-" + index,
                    "notice-token-" + suffix + "-" + index,
                    "ACTIVE");
        }
        entityManager.flush();
    }

    private User persistRecipient(String key, String token, String subscriptionStatus) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        User user = User.builder()
                .email("notice-" + key + "-" + suffix + "@example.test")
                .password("encoded-password")
                .nickname(("notice-" + key).substring(0, Math.min(10, ("notice-" + key).length())))
                .oauthProvider("LOCAL")
                .oauthId("notice-" + key + "-" + suffix)
                .role(UserRole.USER)
                .fcmToken(token)
                .subscriptionStatus(subscriptionStatus)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Notice persistNotice(String title, String content) {
        return noticeRepository.saveAndFlush(Notice.builder()
                .title(title)
                .content(content)
                .isSent(false)
                .sentAt(LocalDateTime.now())
                .build());
    }

    private long notificationCountForNotice(Long... noticeIds) {
        String placeholders = String.join(",", java.util.Collections.nCopies(noticeIds.length, "?"));
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE notice_id IN (" + placeholders + ")",
                Long.class,
                (Object[]) noticeIds);
    }

    private long outboxCountForNotice(Long noticeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fcm_outbox WHERE notice_id = ?",
                Long.class,
                noticeId);
    }

    private long outboxUserId(Long noticeId) {
        return jdbcTemplate.queryForObject(
                "SELECT user_id FROM fcm_outbox WHERE notice_id = ?",
                Long.class,
                noticeId);
    }
}
