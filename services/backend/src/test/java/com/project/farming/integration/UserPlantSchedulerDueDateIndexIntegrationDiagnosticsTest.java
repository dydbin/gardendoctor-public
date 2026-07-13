package com.project.farming.integration;

import com.project.farming.domain.farm.entity.Farm;
import com.project.farming.domain.plant.entity.Plant;
import com.project.farming.domain.user.entity.User;
import com.project.farming.domain.user.entity.UserRole;
import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class UserPlantSchedulerDueDateIndexIntegrationDiagnosticsTest {

    private static final int OTHER_USER_PLANT_COUNT = 50_000;
    private static final int DUE_USER_PLANT_COUNT = 30;
    private static final int LATENCY_WARMUP_ITERATIONS = 5;
    private static final int LATENCY_SAMPLE_ITERATIONS = 25;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void schedulerWateringQueryShouldUseMaterializedDueDateIndex() {
        SeedReferences references = seedReferences();
        String nicknamePrefix = "due";
        LocalDate diagnosticDate = LocalDate.now();
        batchInsertUserPlants(nicknamePrefix, references, diagnosticDate);

        assertThat(indexColumns("user_plants", "idx_userplant_due_watering"))
                .containsExactly("is_notification_enabled", "deleted", "next_watering_date", "watered");
        assertThat(indexColumns("user_plants", "idx_userplant_due_pruning"))
                .containsExactly("is_notification_enabled", "deleted", "next_pruning_date", "pruned");
        assertThat(indexColumns("user_plants", "idx_userplant_due_fertilizing"))
                .containsExactly("is_notification_enabled", "deleted", "next_fertilizing_date", "fertilized");

        String ignoreDueIndexes = noIndexHint("user_plants",
                "idx_userplant_due_watering",
                "idx_userplant_due_pruning",
                "idx_userplant_due_fertilizing",
                "idx_user_plant");

        QueryMeasurement datediffBaseline = measureQuery(
                "DATEDIFF baseline",
                ignoreDueIndexes,
                """
                        SELECT user_plant_id, user_id, plant_id, plant_name, plant_nickname
                        FROM user_plants %s
                        WHERE is_notification_enabled = true
                          AND deleted = false
                          AND last_watered_date IS NOT NULL
                          AND DATEDIFF(%s, last_watered_date) >= water_interval_days
                        """,
                DUE_USER_PLANT_COUNT,
                diagnosticDate);
        QueryMeasurement dueDateIndex = measureQuery(
                "due-date index",
                "FORCE INDEX (idx_userplant_due_watering)",
                """
                        SELECT user_plant_id, user_id, plant_id, plant_name, plant_nickname
                        FROM user_plants %s
                        WHERE is_notification_enabled = true
                          AND deleted = false
                          AND next_watering_date <= %s
                        """,
                DUE_USER_PLANT_COUNT,
                diagnosticDate);

        System.out.printf(
                "UserPlant scheduler due-date index measurement: totalSeedRows=%d, dueRows=%d, " +
                        "datediff[%s], dueDateIndex[%s], p95Improvement=%.2fx%n",
                OTHER_USER_PLANT_COUNT + DUE_USER_PLANT_COUNT,
                DUE_USER_PLANT_COUNT,
                datediffBaseline.summary(),
                dueDateIndex.summary(),
                datediffBaseline.latency().p95Millis() / dueDateIndex.latency().p95Millis()
        );

        assertThat(datediffBaseline.plan().keyName())
                .as("The old DATEDIFF baseline should not use the due-date index.")
                .doesNotContain("idx_userplant_due_watering");
        assertThat(dueDateIndex.plan().keyName())
                .as("The due-date query should use the watering due-date index.")
                .contains("idx_userplant_due_watering");
        assertThat(dueDateIndex.plan().accessType())
                .as("The due-date query should use range access.")
                .isEqualTo("range");
        assertThat(dueDateIndex.plan().rowsEstimate())
                .as("The due-date index should estimate fewer rows than the DATEDIFF baseline.")
                .isLessThan(datediffBaseline.plan().rowsEstimate());
        assertThat(dueDateIndex.latency().p95Nanos())
                .as("The due-date index query should have lower local Docker p95 latency.")
                .isLessThan(datediffBaseline.latency().p95Nanos());
    }

    private SeedReferences seedReferences() {
        String suffix = "userplant-due-date-" + System.nanoTime();
        ImageFile imageFile = ImageFile.builder()
                .originalImageName(suffix + ".png")
                .s3Key(suffix)
                .imageUrl("https://example.test/" + suffix + ".png")
                .domainType(ImageDomainType.USERPLANT)
                .domainId(0L)
                .build();
        entityManager.persist(imageFile);

        User user = User.builder()
                .email(suffix + "@example.com")
                .password("encoded-password")
                .nickname("due" + suffix.substring(Math.max(0, suffix.length() - 12)))
                .oauthProvider("LOCAL")
                .role(UserRole.USER)
                .subscriptionStatus("FREE")
                .build();
        entityManager.persist(user);

        Plant plant = Plant.builder()
                .plantName("due-date-plant-" + suffix)
                .plantEnglishName("Due Date Plant")
                .species("diagnostic")
                .season("all")
                .plantImageFileId(imageFile.getImageFileId())
                .build();
        entityManager.persist(plant);

        Farm farm = Farm.builder()
                .gardenUniqueId(1_700_000_000 + Math.floorMod(Long.hashCode(System.nanoTime()), 100_000_000))
                .operator("diagnostic")
                .farmName("due-date-farm-" + suffix)
                .roadNameAddress("diagnostic road")
                .lotNumberAddress("diagnostic lot")
                .facilities("none")
                .contact("none")
                .latitude(37.5)
                .longitude(127.0)
                .available(true)
                .farmImageFileId(imageFile.getImageFileId())
                .build();
        entityManager.persist(farm);
        entityManager.flush();

        return new SeedReferences(
                user.getUserId(),
                plant.getPlantId(),
                farm.getFarmId(),
                imageFile.getImageFileId());
    }

    private void batchInsertUserPlants(String nicknamePrefix, SeedReferences references, LocalDate today) {
        LocalDateTime now = LocalDateTime.now();

        jdbcTemplate.batchUpdate("""
                        INSERT INTO user_plants (
                            user_id,
                            plant_id,
                            plant_name,
                            plant_nickname,
                            farm_id,
                            planting_place,
                            planted_date,
                            notes,
                            is_notification_enabled,
                            water_interval_days,
                            last_watered_date,
                            next_watering_date,
                            watered,
                            prune_interval_days,
                            last_pruned_date,
                            next_pruning_date,
                            pruned,
                            fertilize_interval_days,
                            last_fertilized_date,
                            next_fertilizing_date,
                            fertilized,
                            user_plant_image_file_id,
                            created_at,
                            updated_at,
                            deleted,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        boolean due = i < DUE_USER_PLANT_COUNT;
                        LocalDate lastWateredDate = due ? today.minusDays(7) : today;
                        LocalDate nextWateringDate = due ? today : today.plusDays(30);

                        ps.setLong(1, references.userId());
                        ps.setLong(2, references.plantId());
                        ps.setString(3, "diagnostic plant");
                        ps.setString(4, nicknamePrefix + i);
                        ps.setLong(5, references.farmId());
                        ps.setString(6, "diagnostic farm");
                        ps.setTimestamp(7, Timestamp.valueOf(now.minusDays(30)));
                        ps.setString(8, "diagnostic");
                        ps.setBoolean(9, true);
                        ps.setInt(10, 7);
                        ps.setDate(11, Date.valueOf(lastWateredDate));
                        ps.setDate(12, Date.valueOf(nextWateringDate));
                        ps.setBoolean(13, false);
                        ps.setInt(14, 14);
                        ps.setDate(15, Date.valueOf(today));
                        ps.setDate(16, Date.valueOf(today.plusDays(14)));
                        ps.setBoolean(17, false);
                        ps.setInt(18, 30);
                        ps.setDate(19, Date.valueOf(today));
                        ps.setDate(20, Date.valueOf(today.plusDays(30)));
                        ps.setBoolean(21, false);
                        ps.setLong(22, references.imageFileId());
                        ps.setTimestamp(23, Timestamp.valueOf(now));
                        ps.setTimestamp(24, Timestamp.valueOf(now));
                        ps.setBoolean(25, false);
                        ps.setLong(26, 0L);
                    }

                    @Override
                    public int getBatchSize() {
                        return OTHER_USER_PLANT_COUNT + DUE_USER_PLANT_COUNT;
                    }
                });
    }

    private QueryMeasurement measureQuery(
            String label, String indexHint, String sqlTemplate, int expectedRows, LocalDate diagnosticDate) {
        String sql = sqlTemplate.formatted(indexHint, "DATE '" + diagnosticDate + "'");
        ExplainRow explainRow = explain(sql);
        LatencyStats latencyStats = measureLatency(sql, expectedRows);
        return new QueryMeasurement(label, explainRow, latencyStats);
    }

    private ExplainRow explain(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("EXPLAIN " + sql);
        return rows.stream()
                .map(this::toExplainRow)
                .filter(row -> row.tableName().equals("user_plants"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("user_plants EXPLAIN row was not found: " + rows));
    }

    private LatencyStats measureLatency(String sql, int expectedRows) {
        for (int i = 0; i < LATENCY_WARMUP_ITERATIONS; i++) {
            jdbcTemplate.queryForList(sql);
        }

        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < LATENCY_SAMPLE_ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            long finishedAt = System.nanoTime();
            assertThat(rows).hasSize(expectedRows);
            elapsedNanos.add(finishedAt - startedAt);
        }
        return LatencyStats.from(elapsedNanos);
    }

    private String noIndexHint(String tableName, String... candidateIndexNames) {
        List<String> existingIndexes = List.of(candidateIndexNames).stream()
                .filter(indexName -> indexExists(tableName, indexName))
                .toList();

        assertThat(existingIndexes)
                .as("The no-index baseline for %s should ignore at least one relevant index.", tableName)
                .isNotEmpty();
        return "IGNORE INDEX (" + String.join(", ", existingIndexes) + ")";
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        """,
                Integer.class,
                tableName,
                indexName);
        return count != null && count > 0;
    }

    private List<String> indexColumns(String tableName, String indexName) {
        return jdbcTemplate.queryForList("""
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        ORDER BY seq_in_index
                        """,
                String.class,
                tableName,
                indexName);
    }

    private ExplainRow toExplainRow(Map<String, Object> row) {
        return new ExplainRow(
                value(row, "table"),
                value(row, "type"),
                value(row, "key"),
                number(row.get("rows"))
        );
    }

    private String value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private record SeedReferences(
            Long userId,
            Long plantId,
            Long farmId,
            Long imageFileId
    ) {
    }

    private record ExplainRow(
            String tableName,
            String accessType,
            String keyName,
            long rowsEstimate
    ) {
    }

    private record QueryMeasurement(
            String label,
            ExplainRow plan,
            LatencyStats latency
    ) {
        String summary() {
            return "%s[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f]".formatted(
                    label,
                    plan.accessType(),
                    plan.keyName(),
                    plan.rowsEstimate(),
                    latency.p50Millis(),
                    latency.p95Millis(),
                    latency.maxMillis());
        }
    }

    private record LatencyStats(
            long p50Nanos,
            long p95Nanos,
            long maxNanos
    ) {
        static LatencyStats from(List<Long> elapsedNanos) {
            List<Long> sorted = elapsedNanos.stream()
                    .sorted()
                    .toList();
            return new LatencyStats(
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    sorted.get(sorted.size() - 1)
            );
        }

        private static long percentile(List<Long> sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        double p50Millis() {
            return nanosToMillis(p50Nanos);
        }

        double p95Millis() {
            return nanosToMillis(p95Nanos);
        }

        double maxMillis() {
            return nanosToMillis(maxNanos);
        }

        private double nanosToMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
