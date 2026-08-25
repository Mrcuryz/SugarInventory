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
    void criticalSchemaContractCoversObservedBackfillAndWriteIdempotencyBoundaries() {
        assertThat(DatabaseSchemaReadinessVerifier.CRITICAL_COLUMNS).contains(
                "pallet_task_semi_item.prepare_balance_id",
                "pallet_task_semi_item.board_count",
                "pallet_task_semi_item.piece_count",
                "pallet_task_semi_item.total_pieces",
                "auto_inbound_execution.request_hash",
                "agent_finish_inbound_execution_preview.state_digest",
                "agent_finish_inbound_execution_confirmation.token_sha256",
                "agent_finish_inbound_execution_request.idempotency_key_sha256");
        assertThat(DatabaseSchemaReadinessVerifier.CRITICAL_INDEXES).contains(
                "auto_inbound_execution.uk_auto_inbound_batch_task",
                "agent_finish_inbound_execution_preview.uk_agent_finish_inbound_preview_ref",
                "agent_finish_inbound_execution_confirmation.uk_finish_inbound_confirmation_preview",
                "agent_finish_inbound_execution_request.uk_finish_inbound_request_idempotency");
        assertThat(DatabaseSchemaReadinessVerifier.CRITICAL_NULLABLE_COLUMNS)
                .containsExactly("pallet_task_semi_item.semi_pallet_code_id");
    }

    @Test
    void criticalSchemaInspectionReportsMissingColumnsAndIndexesInsteadOfTrustingLedgerOnly() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of(
                        "pallet_task_semi_item.prepare_balance_id",
                        "AUTO_INBOUND_EXECUTION.REQUEST_HASH"))
                .thenReturn(List.of("AUTO_INBOUND_EXECUTION.UK_AUTO_INBOUND_BATCH_TASK"))
                .thenReturn(List.of());
        DatabaseSchemaReadinessVerifier verifier = new DatabaseSchemaReadinessVerifier(
                jdbcTemplate,
                new PathMatchingResourcePatternResolver());

        DatabaseSchemaReadinessVerifier.CriticalSchemaShape shape = verifier.inspectCriticalSchemaShape();

        assertThat(shape.missingColumns())
                .doesNotContain("pallet_task_semi_item.prepare_balance_id", "auto_inbound_execution.request_hash")
                .contains("pallet_task_semi_item.board_count",
                        "agent_finish_inbound_execution_confirmation.token_sha256");
        assertThat(shape.missingIndexes())
                .doesNotContain("auto_inbound_execution.uk_auto_inbound_batch_task")
                .contains("agent_finish_inbound_execution_request.uk_finish_inbound_request_idempotency");
        assertThat(shape.missingNullableColumns())
                .containsExactly("pallet_task_semi_item.semi_pallet_code_id");
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
