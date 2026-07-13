package com.project.farming.global.integrity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierReferenceIntegrityReportTest {

    @Test
    void cleanReportShouldHaveZeroTotalOrphans() {
        IdentifierReferenceIntegrityReport report = new IdentifierReferenceIntegrityReport(List.of(
                new IdentifierReferenceOrphan("users.profile_image_file_id", 0),
                new IdentifierReferenceOrphan("diaries.user_id", 0)
        ));

        assertThat(report.checkedReferenceCount()).isEqualTo(2);
        assertThat(report.totalOrphanCount()).isZero();
        assertThat(report.orphanedReferences()).isEmpty();
        assertThat(report.isClean()).isTrue();
    }

    @Test
    void reportShouldExposeOnlyOrphanedReferences() {
        IdentifierReferenceIntegrityReport report = new IdentifierReferenceIntegrityReport(List.of(
                new IdentifierReferenceOrphan("users.profile_image_file_id", 0),
                new IdentifierReferenceOrphan("diaries.user_id", 3)
        ));

        assertThat(report.checkedReferenceCount()).isEqualTo(2);
        assertThat(report.totalOrphanCount()).isEqualTo(3);
        assertThat(report.orphanedReferences())
                .extracting(IdentifierReferenceOrphan::referenceName)
                .containsExactly("diaries.user_id");
        assertThat(report.isClean()).isFalse();
    }

    @Test
    void reportShouldRejectNullReferences() {
        assertThatThrownBy(() -> new IdentifierReferenceIntegrityReport(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("references");
    }
}
