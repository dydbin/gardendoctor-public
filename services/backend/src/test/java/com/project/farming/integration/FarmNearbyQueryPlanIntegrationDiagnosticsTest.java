package com.project.farming.integration;

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

import java.math.BigInteger;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional
class FarmNearbyQueryPlanIntegrationDiagnosticsTest {

    private static final double CENTER_LATITUDE = -66.1234;
    private static final double CENTER_LONGITUDE = 88.5678;
    private static final double RADIUS_KILOMETERS = 5.0;
    private static final double RADIUS_METERS = RADIUS_KILOMETERS * 1000.0;
    private static final double KILOMETERS_PER_LATITUDE_DEGREE = 111.32;
    private static final int FAR_FARM_COUNT = 50_000;
    private static final int NEAR_FARM_COUNT = 20;
    private static final int LATENCY_WARMUP_ITERATIONS = 5;
    private static final int LATENCY_SAMPLE_ITERATIONS = 25;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void nearbyFarmQueryPlanShouldShowLocationIndexRangeAccessOnLargeSeed() {
        SeedStats seedStats = seedLargeFarmSet();
        BoundingBox boundingBox = boundingBox();

        long totalSeedRows = countSeedRows(seedStats.namePrefix());
        long boundingBoxCandidateRows = countSeedBoundingBoxRows(seedStats.namePrefix(), boundingBox);
        ExplainRow noIndexPlan = explainFarmPlan("IGNORE INDEX (idx_farm_location)", boundingBox);
        ExplainRow forcedIndexPlan = explainFarmPlan("FORCE INDEX (idx_farm_location)", boundingBox);
        LatencyStats noIndexLatency = measureFarmQueryLatency("IGNORE INDEX (idx_farm_location)", boundingBox);
        LatencyStats forcedIndexLatency = measureFarmQueryLatency("FORCE INDEX (idx_farm_location)", boundingBox);

        System.out.printf(
                "Farm nearby index measurement: totalSeedRows=%d, boundingBoxCandidates=%d, " +
                        "ignoreIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                        "forceIndex[type=%s,key=%s,rows=%d,p50Ms=%.3f,p95Ms=%.3f,maxMs=%.3f], " +
                        "p95Improvement=%.2fx%n",
                totalSeedRows,
                boundingBoxCandidateRows,
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

        assertThat(totalSeedRows).isEqualTo(seedStats.totalRows());
        assertThat(boundingBoxCandidateRows)
                .as("The bounding box should reduce candidate rows before exact distance calculation.")
                .isEqualTo(NEAR_FARM_COUNT);
        assertThat(noIndexPlan.keyName())
                .as("The no-index baseline should not use the location index.")
                .doesNotContain("idx_farm_location");
        assertThat(noIndexPlan.accessType())
                .as("The no-index baseline should scan farm_info before applying the distance predicate.")
                .isEqualTo("ALL");
        assertThat(forcedIndexPlan.keyName())
                .as("The indexed baseline should use idx_farm_location for the bounding-box range.")
                .contains("idx_farm_location");
        assertThat(forcedIndexPlan.accessType())
                .as("The indexed baseline should use range-style access, not a full scan.")
                .isIn("range", "index_merge");
        assertThat(forcedIndexPlan.rowsEstimate())
                .as("The indexed plan should estimate fewer farm_info rows than the no-index baseline.")
                .isLessThan(noIndexPlan.rowsEstimate());
        assertThat(forcedIndexLatency.p95Nanos())
                .as("The forced-index baseline should have lower local Docker p95 latency than the no-index baseline.")
                .isLessThan(noIndexLatency.p95Nanos());
    }

    private SeedStats seedLargeFarmSet() {
        String namePrefix = "nearby-plan-" + System.nanoTime();
        ImageFile farmImage = image(namePrefix);
        int gardenUniqueIdBase = 1_500_000_000 + Math.floorMod(Long.hashCode(System.nanoTime()), 100_000_000);
        batchInsertFarms(namePrefix, farmImage.getImageFileId(), gardenUniqueIdBase);
        return new SeedStats(namePrefix + "%", NEAR_FARM_COUNT + FAR_FARM_COUNT);
    }

    private void batchInsertFarms(String namePrefix, Long farmImageFileId, int gardenUniqueIdBase) {
        LocalDate today = LocalDate.now();
        jdbcTemplate.batchUpdate("""
                        INSERT INTO farm_info (
                            garden_unique_id,
                            operator,
                            farm_name,
                            road_name_address,
                            lot_number_address,
                            facilities,
                            contact,
                            latitude,
                            longitude,
                            available,
                            farm_image_file_id,
                            created_at,
                            updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        FarmSeedRow row = farmSeedRow(namePrefix, farmImageFileId, gardenUniqueIdBase, i);
                        ps.setInt(1, row.gardenUniqueId());
                        ps.setString(2, "diagnostic");
                        ps.setString(3, row.farmName());
                        ps.setString(4, "diagnostic road");
                        ps.setString(5, "diagnostic lot");
                        ps.setString(6, "none");
                        ps.setString(7, "none");
                        ps.setDouble(8, row.latitude());
                        ps.setDouble(9, row.longitude());
                        ps.setBoolean(10, true);
                        ps.setLong(11, farmImageFileId);
                        ps.setDate(12, Date.valueOf(today));
                        ps.setDate(13, Date.valueOf(today));
                    }

                    @Override
                    public int getBatchSize() {
                        return NEAR_FARM_COUNT + FAR_FARM_COUNT;
                    }
                });
    }

    private FarmSeedRow farmSeedRow(String namePrefix, Long farmImageFileId, int gardenUniqueIdBase, int rowIndex) {
        if (rowIndex < NEAR_FARM_COUNT) {
            double offset = (rowIndex % 10) * 0.0005;
            return new FarmSeedRow(
                    gardenUniqueIdBase + rowIndex,
                    namePrefix + "-near-" + rowIndex,
                    CENTER_LATITUDE + offset,
                    CENTER_LONGITUDE + offset,
                    farmImageFileId
            );
        }

        int farIndex = rowIndex - NEAR_FARM_COUNT;
        double latitude = -40.0 + (farIndex % 500) * 0.01;
        double longitude = 120.0 + (farIndex / 500) * 0.01;
        return new FarmSeedRow(
                gardenUniqueIdBase + rowIndex,
                namePrefix + "-far-" + farIndex,
                latitude,
                longitude,
                farmImageFileId
        );
    }

    private ImageFile image(String namePrefix) {
        ImageFile imageFile = ImageFile.builder()
                .originalImageName(namePrefix + ".png")
                .s3Key(namePrefix)
                .imageUrl("https://example.test/" + namePrefix + ".png")
                .domainType(ImageDomainType.FARM)
                .domainId(0L)
                .build();
        entityManager.persist(imageFile);
        entityManager.flush();
        return imageFile;
    }

    private long countSeedRows(String namePrefix) {
        return ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM farm_info
                        WHERE deleted = false
                          AND farm_name LIKE :namePrefix
                        """)
                .setParameter("namePrefix", namePrefix)
                .getSingleResult()).longValue();
    }

    private long countSeedBoundingBoxRows(String namePrefix, BoundingBox boundingBox) {
        return ((Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM farm_info
                        WHERE deleted = false
                          AND farm_name LIKE :namePrefix
                          AND latitude BETWEEN :minLatitude AND :maxLatitude
                          AND longitude BETWEEN :minLongitude AND :maxLongitude
                        """)
                .setParameter("namePrefix", namePrefix)
                .setParameter("minLatitude", boundingBox.minLatitude())
                .setParameter("maxLatitude", boundingBox.maxLatitude())
                .setParameter("minLongitude", boundingBox.minLongitude())
                .setParameter("maxLongitude", boundingBox.maxLongitude())
                .getSingleResult()).longValue();
    }

    private ExplainRow explainFarmPlan(String indexHint, BoundingBox boundingBox) {
        List<?> rows = entityManager.createNativeQuery("""
                        EXPLAIN
                        SELECT
                            f.farm_id AS farmId,
                            f.garden_unique_id AS gardenUniqueId,
                            f.operator AS operator,
                            f.farm_name AS farmName,
                            f.road_name_address AS roadNameAddress,
                            f.lot_number_address AS lotNumberAddress,
                            f.facilities AS facilities,
                            f.contact AS contact,
                            f.latitude AS latitude,
                            f.longitude AS longitude,
                            f.available AS available,
                            f.created_at AS createdAt,
                            f.updated_at AS updatedAt,
                            image.image_url AS farmImageUrl
                        FROM farm_info f %s
                        STRAIGHT_JOIN image_files image ON image.image_file_id = f.farm_image_file_id
                        WHERE f.deleted = false
                          AND f.latitude BETWEEN :minLatitude AND :maxLatitude
                          AND f.longitude BETWEEN :minLongitude AND :maxLongitude
                          AND ST_Distance_Sphere(
                              POINT(:longitude, :latitude),
                              POINT(f.longitude, f.latitude)
                          ) <= :radiusMeters
                        ORDER BY ST_Distance_Sphere(
                              POINT(:longitude, :latitude),
                              POINT(f.longitude, f.latitude)
                          ) ASC,
                          f.garden_unique_id ASC
                        """.formatted(indexHint))
                .setParameter("latitude", CENTER_LATITUDE)
                .setParameter("longitude", CENTER_LONGITUDE)
                .setParameter("radiusMeters", RADIUS_METERS)
                .setParameter("minLatitude", boundingBox.minLatitude())
                .setParameter("maxLatitude", boundingBox.maxLatitude())
                .setParameter("minLongitude", boundingBox.minLongitude())
                .setParameter("maxLongitude", boundingBox.maxLongitude())
                .getResultList();

        return rows.stream()
                .map(Object[].class::cast)
                .map(this::toExplainRow)
                .filter(row -> row.tableName().equals("f") || row.tableName().equals("farm_info"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("farm_info EXPLAIN row was not found: " + rows));
    }

    private LatencyStats measureFarmQueryLatency(String indexHint, BoundingBox boundingBox) {
        for (int i = 0; i < LATENCY_WARMUP_ITERATIONS; i++) {
            executeFarmNearbyQuery(indexHint, boundingBox);
        }

        List<Long> elapsedNanos = new ArrayList<>();
        for (int i = 0; i < LATENCY_SAMPLE_ITERATIONS; i++) {
            long startedAt = System.nanoTime();
            List<?> rows = executeFarmNearbyQuery(indexHint, boundingBox);
            long finishedAt = System.nanoTime();
            assertThat(rows).hasSize(NEAR_FARM_COUNT);
            elapsedNanos.add(finishedAt - startedAt);
        }

        return LatencyStats.from(elapsedNanos);
    }

    private List<?> executeFarmNearbyQuery(String indexHint, BoundingBox boundingBox) {
        return entityManager.createNativeQuery("""
                        SELECT
                            f.farm_id AS farmId,
                            f.garden_unique_id AS gardenUniqueId,
                            f.operator AS operator,
                            f.farm_name AS farmName,
                            f.road_name_address AS roadNameAddress,
                            f.lot_number_address AS lotNumberAddress,
                            f.facilities AS facilities,
                            f.contact AS contact,
                            f.latitude AS latitude,
                            f.longitude AS longitude,
                            f.available AS available,
                            f.created_at AS createdAt,
                            f.updated_at AS updatedAt,
                            image.image_url AS farmImageUrl
                        FROM farm_info f %s
                        STRAIGHT_JOIN image_files image ON image.image_file_id = f.farm_image_file_id
                        WHERE f.deleted = false
                          AND f.latitude BETWEEN :minLatitude AND :maxLatitude
                          AND f.longitude BETWEEN :minLongitude AND :maxLongitude
                          AND ST_Distance_Sphere(
                              POINT(:longitude, :latitude),
                              POINT(f.longitude, f.latitude)
                          ) <= :radiusMeters
                        ORDER BY ST_Distance_Sphere(
                              POINT(:longitude, :latitude),
                              POINT(f.longitude, f.latitude)
                          ) ASC,
                          f.garden_unique_id ASC
                        """.formatted(indexHint))
                .setParameter("latitude", CENTER_LATITUDE)
                .setParameter("longitude", CENTER_LONGITUDE)
                .setParameter("radiusMeters", RADIUS_METERS)
                .setParameter("minLatitude", boundingBox.minLatitude())
                .setParameter("maxLatitude", boundingBox.maxLatitude())
                .setParameter("minLongitude", boundingBox.minLongitude())
                .setParameter("maxLongitude", boundingBox.maxLongitude())
                .getResultList();
    }

    private ExplainRow toExplainRow(Object[] row) {
        if (row.length >= 12) {
            return new ExplainRow(
                    value(row[2]),
                    value(row[4]),
                    value(row[6]),
                    number(row[9])
            );
        }
        return new ExplainRow(
                value(row[2]),
                value(row[3]),
                value(row[5]),
                number(row[8])
        );
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private long number(Object value) {
        if (value instanceof BigInteger bigInteger) {
            return bigInteger.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private BoundingBox boundingBox() {
        double latitudeDelta = RADIUS_KILOMETERS / KILOMETERS_PER_LATITUDE_DEGREE;
        double longitudeDelta = RADIUS_KILOMETERS / (
                KILOMETERS_PER_LATITUDE_DEGREE * Math.abs(Math.cos(Math.toRadians(CENTER_LATITUDE)))
        );
        return new BoundingBox(
                CENTER_LATITUDE - latitudeDelta,
                CENTER_LATITUDE + latitudeDelta,
                CENTER_LONGITUDE - longitudeDelta,
                CENTER_LONGITUDE + longitudeDelta
        );
    }

    private record BoundingBox(
            double minLatitude,
            double maxLatitude,
            double minLongitude,
            double maxLongitude
    ) {
    }

    private record SeedStats(String namePrefix, int totalRows) {
    }

    private record FarmSeedRow(
            int gardenUniqueId,
            String farmName,
            double latitude,
            double longitude,
            Long farmImageFileId
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
