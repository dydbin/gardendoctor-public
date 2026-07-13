package com.project.farming.integration;

import com.project.farming.domain.userplant.entity.UserPlant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.hibernate.annotations.Check;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class ValidationSchemaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void noticeAndNotificationColumnLengthsShouldMatchRequestContracts() {
        assertThat(columnLength("notices", "title")).isEqualTo(100L);
        assertThat(columnLength("notices", "content")).isEqualTo(500L);
        assertThat(columnLength("notification", "title")).isEqualTo(100L);
        assertThat(columnLength("notification", "message")).isEqualTo(500L);
    }

    @Test
    void userPlantEntitySchemaShouldDeclareCareIntervalCheckConstraint() {
        Check check = UserPlant.class.getAnnotation(Check.class);

        assertThat(check).isNotNull();
        assertThat(check.constraints().toLowerCase())
                .contains("water_interval_days", "prune_interval_days", "fertilize_interval_days")
                .contains("365");
    }

    private long columnLength(String tableName, String columnName) {
        Long length = jdbcTemplate.queryForObject("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Long.class, tableName, columnName);
        return length == null ? -1L : length;
    }
}
