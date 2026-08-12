package com.Laibin.SugarInventory.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseSchemaReadinessVerifierTest {

    @Test
    void packagedMigrationContractsCoverLatestRuntimeTables() {
        DatabaseSchemaReadinessVerifier verifier = new DatabaseSchemaReadinessVerifier(
                mock(JdbcTemplate.class),
                new PathMatchingResourcePatternResolver());

        Map<String, DatabaseSchemaReadinessVerifier.MigrationContract> contracts = verifier.loadMigrationContracts();

        assertThat(contracts).hasSizeGreaterThanOrEqualTo(41);
        assertThat(contracts).containsKeys(
                "2026-08-01-add-inventory-history-job-evidence.sql",
                "2026-08-12-auto-inbound-execution-idempotency.sql",
                "2026-08-12-backfill-pallet-task-semi-item-prepare-fields.sql",
                "2026-08-12-complete-business-write-permission-matrix.sql");
        assertThat(contracts.values().stream()
                .flatMap(contract -> contract.createdTables().stream()))
                .contains("inventory_history_job_run", "auto_inbound_execution");
    }

    @Test
    void correctiveSemiItemMigrationLocksRuntimeColumnsAndNullableLegacyReference() throws Exception {
        String migration = Files.readString(Path.of("migrations",
                "2026-08-12-backfill-pallet-task-semi-item-prepare-fields.sql"), StandardCharsets.UTF_8);

        assertThat(migration).contains(
                "prepare_balance_id",
                "board_count",
                "piece_count",
                "total_pieces",
                "MODIFY COLUMN semi_pallet_code_id INT NULL",
                "information_schema.COLUMNS");
    }

    @Test
    void createdTableExtractionHandlesQuotedAndConditionalStatements() {
        Set<String> tables = DatabaseSchemaReadinessVerifier.extractCreatedTables("""
                CREATE TABLE IF NOT EXISTS first_table (id BIGINT);
                CREATE TABLE `second_table` (id BIGINT);
                """);

        assertThat(tables).containsExactly("first_table", "second_table");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void startupGateRejectsMissingMigrationLedgerEntries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class))).thenReturn(Map.of());
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of());
        DatabaseSchemaReadinessVerifier verifier = new DatabaseSchemaReadinessVerifier(
                jdbcTemplate,
                new PathMatchingResourcePatternResolver());

        assertThatThrownBy(() -> verifier.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missingMigrations")
                .hasMessageContaining("2026-08-12-auto-inbound-execution-idempotency.sql");
    }
}
