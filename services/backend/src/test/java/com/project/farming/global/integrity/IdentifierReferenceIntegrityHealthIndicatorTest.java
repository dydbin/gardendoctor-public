package com.project.farming.global.integrity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentifierReferenceIntegrityHealthIndicatorTest {

    @Test
    void healthShouldBeUpWhenNoOrphansExist() {
        IdentifierReferenceIntegrityService integrityService = mock(IdentifierReferenceIntegrityService.class);
        when(integrityService.inspect()).thenReturn(new IdentifierReferenceIntegrityReport(List.of(
                new IdentifierReferenceOrphan("users.profile_image_file_id", 0),
                new IdentifierReferenceOrphan("diaries.user_id", 0)
        )));
        IdentifierReferenceIntegrityHealthIndicator healthIndicator =
                new IdentifierReferenceIntegrityHealthIndicator(integrityService);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("checkedReferenceCount", 2)
                .containsEntry("totalOrphanCount", 0L)
                .doesNotContainKey("orphanCounts");
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthShouldBeDownWhenOrphansExist() {
        IdentifierReferenceIntegrityService integrityService = mock(IdentifierReferenceIntegrityService.class);
        when(integrityService.inspect()).thenReturn(new IdentifierReferenceIntegrityReport(List.of(
                new IdentifierReferenceOrphan("users.profile_image_file_id", 0),
                new IdentifierReferenceOrphan("diaries.user_id", 4)
        )));
        IdentifierReferenceIntegrityHealthIndicator healthIndicator =
                new IdentifierReferenceIntegrityHealthIndicator(integrityService);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("checkedReferenceCount", 2)
                .containsEntry("totalOrphanCount", 4L);
        assertThat((Map<String, Long>) health.getDetails().get("orphanCounts"))
                .containsExactly(Map.entry("diaries.user_id", 4L));
    }
}
