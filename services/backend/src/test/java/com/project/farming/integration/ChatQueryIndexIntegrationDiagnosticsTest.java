package com.project.farming.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
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
class ChatQueryIndexIntegrationDiagnosticsTest {

    private static final String INDEX_NAME = "idx_chat_user";
    private static final int TARGET_ROWS = 200;
    private static final int OTHER_ROWS = 50_000;
    private static final int PAGE_SIZE = 20;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int SAMPLE_ITERATIONS = 25;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void chatUserPageShouldUseInnoDbUserIndexWithImplicitPrimaryKeyOrdering() {
        long seed = Math.floorMod(System.nanoTime(), 100_000_000L);
        long targetUserId = 8_000_000_000L + seed;
        long otherUserId = targetUserId + 1;
        batchInsertChats(targetUserId, otherUserId, seed);

        assertThat(indexColumns()).containsExactly("user_id");

        String pageSql = """
                SELECT chat_id, python_session_id
                FROM chat %s
                WHERE user_id = ?
                ORDER BY chat_id DESC
                LIMIT 20
                """;
        String countSql = """
                SELECT COUNT(*)
                FROM chat %s
                WHERE user_id = ?
                """;

        QueryMeasurement page = measureListQuery(pageSql, targetUserId);
        QueryMeasurement count = measureCountQuery(countSql, targetUserId);

        System.out.printf(
                "Chat index measurement: rows=%d, page[%s], count[%s]%n",
                TARGET_ROWS + OTHER_ROWS,
                page.summary(),
                count.summary());

        assertThat(page.optimizedPlan().key()).isEqualTo(INDEX_NAME);
        assertThat(count.optimizedPlan().key()).isEqualTo(INDEX_NAME);
        assertThat(page.baselinePlan().key()).isNotEqualTo(INDEX_NAME);
        assertThat(count.baselinePlan().key()).isNotEqualTo(INDEX_NAME);
        assertThat(page.optimizedP95Millis()).isLessThan(page.baselineP95Millis());
        assertThat(count.optimizedP95Millis()).isLessThan(count.baselineP95Millis());
    }

    private QueryMeasurement measureListQuery(String sqlTemplate, long userId) {
        String baselineSql = sqlTemplate.formatted("IGNORE INDEX (" + INDEX_NAME + ")");
        String optimizedSql = sqlTemplate.formatted("FORCE INDEX (" + INDEX_NAME + ")");
        ExplainPlan baselinePlan = explain(baselineSql, userId);
        ExplainPlan optimizedPlan = explain(optimizedSql, userId);
        double baselineP95 = measureP95(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(baselineSql, userId);
            assertThat(rows).hasSize(PAGE_SIZE);
        });
        double optimizedP95 = measureP95(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(optimizedSql, userId);
            assertThat(rows).hasSize(PAGE_SIZE);
        });
        return new QueryMeasurement(baselinePlan, optimizedPlan, baselineP95, optimizedP95);
    }

    private QueryMeasurement measureCountQuery(String sqlTemplate, long userId) {
        String baselineSql = sqlTemplate.formatted("IGNORE INDEX (" + INDEX_NAME + ")");
        String optimizedSql = sqlTemplate.formatted("FORCE INDEX (" + INDEX_NAME + ")");
        ExplainPlan baselinePlan = explain(baselineSql, userId);
        ExplainPlan optimizedPlan = explain(optimizedSql, userId);
        double baselineP95 = measureP95(() -> assertThat(
                jdbcTemplate.queryForObject(baselineSql, Long.class, userId)).isEqualTo(TARGET_ROWS));
        double optimizedP95 = measureP95(() -> assertThat(
                jdbcTemplate.queryForObject(optimizedSql, Long.class, userId)).isEqualTo(TARGET_ROWS));
        return new QueryMeasurement(baselinePlan, optimizedPlan, baselineP95, optimizedP95);
    }

    private ExplainPlan explain(String sql, long userId) {
        Map<String, Object> row = jdbcTemplate.queryForMap("EXPLAIN " + sql, userId);
        return new ExplainPlan(
                stringValue(row.get("key")),
                numberValue(row.get("rows")),
                stringValue(row.get("Extra")));
    }

    private double measureP95(Runnable query) {
        for (int index = 0; index < WARMUP_ITERATIONS; index++) {
            query.run();
        }
        List<Long> elapsedNanos = new ArrayList<>();
        for (int index = 0; index < SAMPLE_ITERATIONS; index++) {
            long startedAt = System.nanoTime();
            query.run();
            elapsedNanos.add(System.nanoTime() - startedAt);
        }
        elapsedNanos.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(elapsedNanos.size() * 0.95) - 1;
        return elapsedNanos.get(p95Index) / 1_000_000.0;
    }

    private List<String> indexColumns() {
        return jdbcTemplate.queryForList("""
                        SELECT column_name
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = 'chat'
                          AND index_name = ?
                        ORDER BY seq_in_index
                        """,
                String.class,
                INDEX_NAME);
    }

    private void batchInsertChats(long targetUserId, long otherUserId, long seed) {
        jdbcTemplate.batchUpdate("INSERT INTO chat (user_id, python_session_id) VALUES (?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {
                        boolean target = index < TARGET_ROWS;
                        ps.setLong(1, target ? targetUserId : otherUserId);
                        ps.setLong(2, 9_000_000_000L + seed + index);
                    }

                    @Override
                    public int getBatchSize() {
                        return TARGET_ROWS + OTHER_ROWS;
                    }
                });
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : -1;
    }

    private record ExplainPlan(String key, long rows, String extra) {
    }

    private record QueryMeasurement(
            ExplainPlan baselinePlan,
            ExplainPlan optimizedPlan,
            double baselineP95Millis,
            double optimizedP95Millis) {

        private String summary() {
            return "baselineKey=%s, baselineRows=%d, baselineP95=%.3fms, "
                    .formatted(baselinePlan.key(), baselinePlan.rows(), baselineP95Millis)
                    + "optimizedKey=%s, optimizedRows=%d, optimizedP95=%.3fms, improvement=%.2fx"
                    .formatted(
                            optimizedPlan.key(),
                            optimizedPlan.rows(),
                            optimizedP95Millis,
                            baselineP95Millis / optimizedP95Millis);
        }
    }
}
