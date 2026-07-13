package com.project.farming.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("portfolio-integration-diagnostic")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
@Transactional(readOnly = true)
class PhysicalForeignKeyInventoryIntegrationDiagnosticsTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void currentSchemaShouldExposePhysicalForeignKeyInventoryForMigrationDecision() {
        List<ForeignKeyRow> foreignKeys = jdbcTemplate.query("""
                        SELECT
                            table_name,
                            constraint_name,
                            column_name,
                            referenced_table_name,
                            referenced_column_name
                        FROM information_schema.key_column_usage
                        WHERE constraint_schema = DATABASE()
                          AND referenced_table_name IS NOT NULL
                        ORDER BY table_name, constraint_name, ordinal_position
                        """,
                (rs, rowNum) -> new ForeignKeyRow(
                        rs.getString("table_name"),
                        rs.getString("constraint_name"),
                        rs.getString("column_name"),
                        rs.getString("referenced_table_name"),
                        rs.getString("referenced_column_name")
                ));

        System.out.printf("Physical FK inventory: count=%d, rows=%s%n", foreignKeys.size(), foreignKeys);

        assertThat(foreignKeys)
                .as("The inventory query should return well-formed FK rows when legacy physical FKs exist.")
                .allSatisfy(row -> {
                    assertThat(row.tableName()).isNotBlank();
                    assertThat(row.constraintName()).isNotBlank();
                    assertThat(row.columnName()).isNotBlank();
                    assertThat(row.referencedTableName()).isNotBlank();
                    assertThat(row.referencedColumnName()).isNotBlank();
                });
    }

    private record ForeignKeyRow(
            String tableName,
            String constraintName,
            String columnName,
            String referencedTableName,
            String referencedColumnName
    ) {
    }
}
