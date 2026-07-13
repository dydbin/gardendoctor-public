package com.project.farming.integration;

import com.project.farming.global.image.entity.ImageDomainType;
import com.project.farming.global.image.entity.ImageFile;
import com.project.farming.global.image.repository.ImageFileRepository;
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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class ImageFileDomainLookupIndexIntegrationDiagnosticsTest {

    private static final String TARGET_DOMAIN_TYPE = "DIARY";
    private static final long TARGET_DOMAIN_ID = 987_654_321L;
    private static final int OTHER_IMAGE_COUNT = 50_000;
    private static final int TARGET_IMAGE_COUNT = 20;
    private static final int LATENCY_WARMUP_ITERATIONS = 5;
    private static final int LATENCY_SAMPLE_ITERATIONS = 25;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ImageFileRepository imageFileRepository;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void publicDeleteOwnershipLookupShouldMatchOnlyOwnedUserImage() {
        String suffix = "image-ownership-" + System.nanoTime();
        ImageFile ownedUserImage = imageFileRepository.save(image(
                suffix + "-owned", ImageDomainType.USER, 101L));
        ImageFile anotherUserImage = imageFileRepository.save(image(
                suffix + "-other-user", ImageDomainType.USER, 202L));
        ImageFile diaryImage = imageFileRepository.save(image(
                suffix + "-diary", ImageDomainType.DIARY, 101L));
        imageFileRepository.flush();

        int ownedMatches = imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                ownedUserImage.getImageFileId(), ImageDomainType.USER, 101L).isPresent() ? 1 : 0;
        int wrongOwnerMatches = imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                anotherUserImage.getImageFileId(), ImageDomainType.USER, 101L).isPresent() ? 1 : 0;
        int nonUserDomainMatches = imageFileRepository.findByImageFileIdAndDomainTypeAndDomainId(
                diaryImage.getImageFileId(), ImageDomainType.USER, 101L).isPresent() ? 1 : 0;

        System.out.printf(
                "Image public-delete ownership lookup: owned=%d, wrongOwner=%d, nonUserDomain=%d%n",
                ownedMatches,
                wrongOwnerMatches,
                nonUserDomainMatches);

        assertThat(ownedMatches).isEqualTo(1);
        assertThat(wrongOwnerMatches).isZero();
        assertThat(nonUserDomainMatches).isZero();
    }

    @Test
    void imageDomainLookupShouldUsePredicateFirstIndex() {
        String s3KeyPrefix = "image-domain-index-" + System.nanoTime();
        batchInsertImages(s3KeyPrefix);

        List<String> indexColumns = imageDomainLookupIndexColumns();
        String noIndexHint = imageNoIndexHint();
        long totalSeedRows = countSeedRows(s3KeyPrefix);
        long targetRows = countTargetRows();
        ExplainRow noIndexPlan = explainImageDomainLookup(noIndexHint);
        ExplainRow forcedIndexPlan = explainImageDomainLookup("FORCE INDEX (idx_image_domain_lookup)");
        LatencyStats noIndexLatency = measureImageDomainLookupLatency(noIndexHint);
        LatencyStats forcedIndexLatency = measureImageDomainLookupLatency("FORCE INDEX (idx_image_domain_lookup)");

        System.out.printf(
                "ImageFile domain lookup index measurement: totalSeedRows=%d, targetRows=%d, " +
                        "indexColumns=%s, ignoreIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                        "forceIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], p95Improvement=%.2fx%n",
                totalSeedRows,
                targetRows,
                indexColumns,
                noIndexPlan.accessType(),
                noIndexPlan.keyName(),
                noIndexPlan.rowsEstimate(),
                noIndexLatency.p50Millis(),
                noIndexLatency.p95Millis(),
                noIndexLatency.maxMillis(),
                forcedIndexPlan.accessType(),
                forcedIndexPlan.keyName(),
                forcedIndexPlan.rowsEstimate(),
                forcedIndexLatency.p50Millis(),
                forcedIndexLatency.p95Millis(),
                forcedIndexLatency.maxMillis(),
                noIndexLatency.p95Millis() / forcedIndexLatency.p95Millis()
        );

        assertThat(indexColumns)
                .as("The source schema should create an index whose leading columns match the repository predicate.")
                .containsExactly("domain_type", "domain_id", "image_file_id");
        assertThat(totalSeedRows).isEqualTo(OTHER_IMAGE_COUNT + TARGET_IMAGE_COUNT);
        assertThat(targetRows).isEqualTo(TARGET_IMAGE_COUNT);
        assertThat(noIndexPlan.keyName())
                .as("The no-index baseline should not use the domain lookup index.")
                .doesNotContain("idx_image_domain_lookup");
        assertThat(forcedIndexPlan.keyName())
                .as("The indexed baseline should use the domain lookup index.")
                .contains("idx_image_domain_lookup");
        assertThat(forcedIndexPlan.accessType())
                .as("The indexed baseline should use key lookup access, not a full table scan.")
                .isIn("ref", "range");
        assertThat(forcedIndexPlan.rowsEstimate())
                .as("The domain lookup index should estimate fewer rows than the no-index baseline.")
                .isLessThan(noIndexPlan.rowsEstimate());
        assertThat(forcedIndexLatency.p95Nanos())
                .as("The forced-index baseline should have lower local Docker p95 latency than the no-index baseline.")
                .isLessThan(noIndexLatency.p95Nanos());
    }

    private ImageFile image(String key, ImageDomainType domainType, Long domainId) {
        return ImageFile.builder()
                .originalImageName(key + ".jpg")
                .s3Key(key)
                .imageUrl("https://example.test/" + key + ".jpg")
                .domainType(domainType)
                .domainId(domainId)
                .build();
    }

    private void batchInsertImages(String s3KeyPrefix) {
        jdbcTemplate.batchUpdate("""
                        INSERT INTO image_files (
                            original_image_name,
                            s3key,
                            image_url,
                            domain_type,
                            domain_id
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ImageSeedRow row = imageSeedRow(s3KeyPrefix, i);
                        ps.setString(1, row.originalImageName());
                        ps.setString(2, row.s3Key());
                        ps.setString(3, row.imageUrl());
                        ps.setString(4, row.domainType());
                        ps.setLong(5, row.domainId());
                    }

                    @Override
                    public int getBatchSize() {
                        return OTHER_IMAGE_COUNT + TARGET_IMAGE_COUNT;
                    }
                });
    }

    private ImageSeedRow imageSeedRow(String s3KeyPrefix, int rowIndex) {
        if (rowIndex < TARGET_IMAGE_COUNT) {
            String s3Key = s3KeyPrefix + "-target-" + rowIndex;
            return new ImageSeedRow(
                    s3Key + ".png",
                    s3Key,
                    "https://example.test/" + s3Key + ".png",
                    TARGET_DOMAIN_TYPE,
                    TARGET_DOMAIN_ID
            );
        }

        int otherIndex = rowIndex - TARGET_IMAGE_COUNT;
        String s3Key = s3KeyPrefix + "-other-" + otherIndex;
        return new ImageSeedRow(
                s3Key + ".png",
                s3Key,
                "https://example.test/" + s3Key + ".png",
                domainTypeFor(otherIndex),
                TARGET_DOMAIN_ID + otherIndex + 1
        );
    }

    private String domainTypeFor(int index) {
        return switch (index % 6) {
            case 0 -> "USER";
            case 1 -> "PLANT";
            case 2 -> "DIARY";
            case 3 -> "FARM";
            case 4 -> "USERPLANT";
            default -> "PHOTO";
        };
    }

    private List<String> imageDomainLookupIndexColumns() {
        return jdbcTemplate.queryForList("""
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'image_files'
                          AND index_name = 'idx_image_domain_lookup'
                        ORDER BY seq_in_index
                        """,
                String.class);
    }

    private String imageNoIndexHint() {
        List<String> ignoredIndexes = imageIndexNames().stream()
                .filter(indexName -> indexName.equals("idx_image_domain_lookup")
                        || indexName.equals("idx_covering_image_file"))
                .toList();

        assertThat(ignoredIndexes)
                .as("The no-index baseline should at least ignore the current domain lookup index.")
                .contains("idx_image_domain_lookup");

        return "IGNORE INDEX (" + String.join(", ", ignoredIndexes) + ")";
    }

    private List<String> imageIndexNames() {
        return jdbcTemplate.queryForList("""
                        SELECT DISTINCT index_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'image_files'
                        ORDER BY index_name
                        """,
                String.class);
    }

    private long countSeedRows(String s3KeyPrefix) {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM image_files
                        WHERE s3key LIKE ?
                        """,
                Long.class,
                s3KeyPrefix + "%");
    }

    private long countTargetRows() {
        return jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM image_files
                        WHERE domain_type = ?
                          AND domain_id = ?
                        """,
                Long.class,
                TARGET_DOMAIN_TYPE,
                TARGET_DOMAIN_ID);
    }

    private ExplainRow explainImageDomainLookup(String indexHint) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        EXPLAIN
                        SELECT image_file_id, image_url, domain_type, domain_id
                        FROM image_files %s
                        WHERE domain_type = ?
                          AND domain_id = ?
                        ORDER BY image_file_id ASC
                        """.formatted(indexHint),
                TARGET_DOMAIN_TYPE,
                TARGET_DOMAIN_ID);

        return rows.stream()
                .map(this::toExplainRow)
                .filter(row -> row.tableName().equals("image_files"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("image_files EXPLAIN row was not found: " + rows));
    }

    private LatencyStats measureImageDomainLookupLatency(String indexHint) {
        for (int i = 0; i < LATENCY_WARMUP_ITERATIONS; i++) {
            executeImageDomainLookup(indexHint);
        }

        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < LATENCY_SAMPLE_ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            List<Map<String, Object>> rows = executeImageDomainLookup(indexHint);
            long finishedAt = System.nanoTime();
            assertThat(rows).hasSize(TARGET_IMAGE_COUNT);
            elapsedNanos.add(finishedAt - startedAt);
        }

        return LatencyStats.from(elapsedNanos);
    }

    private List<Map<String, Object>> executeImageDomainLookup(String indexHint) {
        return jdbcTemplate.queryForList("""
                        SELECT image_file_id, image_url, domain_type, domain_id
                        FROM image_files %s
                        WHERE domain_type = ?
                          AND domain_id = ?
                        ORDER BY image_file_id ASC
                        """.formatted(indexHint),
                TARGET_DOMAIN_TYPE,
                TARGET_DOMAIN_ID);
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

    private record ImageSeedRow(
            String originalImageName,
            String s3Key,
            String imageUrl,
            String domainType,
            long domainId
    ) {
    }

    private record ExplainRow(
            String tableName,
            String accessType,
            String keyName,
            long rowsEstimate
    ) {
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
