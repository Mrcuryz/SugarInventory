package com.Laibin.SugarInventory.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "database.schema", name = "verification-enabled", havingValue = "true")
public class DatabaseSchemaReadinessVerifier implements ApplicationRunner {

    static final String MIGRATION_PATTERN = "classpath*:database/migrations/*.sql";
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-zA-Z0-9_]+)`?");
    private static final Set<String> BASE_TABLES = Set.of(
            "user",
            "employee_roster",
            "role",
            "permission",
            "role_permission",
            "product",
            "warehouse",
            "inventory",
            "pallet_code",
            "pallet_task",
            "pallet_flow_record",
            "operation_log",
            "schema_migration");
    static final Set<String> CRITICAL_COLUMNS = Set.of(
            "pallet_task_semi_item.semi_pallet_code_id",
            "pallet_task_semi_item.prepare_balance_id",
            "pallet_task_semi_item.board_count",
            "pallet_task_semi_item.piece_count",
            "pallet_task_semi_item.total_pieces",
            "auto_inbound_execution.batch_id",
            "auto_inbound_execution.source_task_id",
            "auto_inbound_execution.request_hash",
            "auto_inbound_execution.status",
            "auto_inbound_execution.result_json",
            "auto_inbound_execution.created_by",
            "agent_finish_inbound_execution_preview.preview_ref",
            "agent_finish_inbound_execution_preview.owner_user_id",
            "agent_finish_inbound_execution_preview.agent_session_id",
            "agent_finish_inbound_execution_preview.status",
            "agent_finish_inbound_execution_preview.state_digest",
            "agent_finish_inbound_execution_preview.content_sha256",
            "agent_finish_inbound_execution_preview.expires_at",
            "agent_finish_inbound_execution_confirmation.confirmation_ref",
            "agent_finish_inbound_execution_confirmation.preview_id",
            "agent_finish_inbound_execution_confirmation.owner_user_id",
            "agent_finish_inbound_execution_confirmation.agent_session_id",
            "agent_finish_inbound_execution_confirmation.status",
            "agent_finish_inbound_execution_confirmation.token_sha256",
            "agent_finish_inbound_execution_confirmation.idempotency_key_sha256",
            "agent_finish_inbound_execution_confirmation.expires_at",
            "agent_finish_inbound_execution_request.execution_ref",
            "agent_finish_inbound_execution_request.confirmation_id",
            "agent_finish_inbound_execution_request.owner_user_id",
            "agent_finish_inbound_execution_request.agent_session_id",
            "agent_finish_inbound_execution_request.idempotency_key_sha256",
            "agent_finish_inbound_execution_request.request_sha256",
            "agent_finish_inbound_execution_request.status");
    static final Set<String> CRITICAL_INDEXES = Set.of(
            "auto_inbound_execution.uk_auto_inbound_batch_task",
            "agent_finish_inbound_execution_preview.uk_agent_finish_inbound_preview_ref",
            "agent_finish_inbound_execution_confirmation.uk_finish_inbound_confirmation_ref",
            "agent_finish_inbound_execution_confirmation.uk_finish_inbound_confirmation_preview",
            "agent_finish_inbound_execution_request.uk_finish_inbound_execution_ref",
            "agent_finish_inbound_execution_request.uk_finish_inbound_request_confirmation",
            "agent_finish_inbound_execution_request.uk_finish_inbound_request_idempotency");
    static final Set<String> CRITICAL_NULLABLE_COLUMNS = Set.of(
            "pallet_task_semi_item.semi_pallet_code_id");

    private final JdbcTemplate jdbcTemplate;
    private final ResourcePatternResolver resourcePatternResolver;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, MigrationContract> expected = loadMigrationContracts();
        Map<String, String> applied = loadAppliedMigrations();

        List<String> missingMigrations = expected.keySet().stream()
                .filter(name -> !applied.containsKey(name))
                .toList();
        List<String> checksumMismatches = expected.entrySet().stream()
                .filter(entry -> applied.containsKey(entry.getKey()))
                .filter(entry -> !entry.getValue().checksum().equals(applied.get(entry.getKey())))
                .map(Map.Entry::getKey)
                .toList();

        Set<String> requiredTables = new TreeSet<>(BASE_TABLES);
        expected.values().forEach(contract -> requiredTables.addAll(contract.createdTables()));
        Set<String> actualTables = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()",
                String.class));
        Set<String> missingTables = new TreeSet<>(requiredTables);
        missingTables.removeAll(actualTables);
        CriticalSchemaShape criticalShape = inspectCriticalSchemaShape();

        if (!missingMigrations.isEmpty() || !checksumMismatches.isEmpty() || !missingTables.isEmpty()
                || !criticalShape.missingColumns().isEmpty()
                || !criticalShape.missingIndexes().isEmpty()
                || !criticalShape.missingNullableColumns().isEmpty()) {
            throw new IllegalStateException("Database schema is not ready; missingMigrations=" + missingMigrations
                    + "; checksumMismatches=" + checksumMismatches
                    + "; missingTables=" + missingTables
                    + "; missingCriticalColumns=" + criticalShape.missingColumns()
                    + "; missingCriticalIndexes=" + criticalShape.missingIndexes()
                    + "; missingCriticalNullableColumns=" + criticalShape.missingNullableColumns()
                    + ". Run the controlled database migration command before application startup.");
        }
        log.info("Database schema verification passed: migrations={}, requiredTables={}, criticalColumns={}, criticalIndexes={}, criticalNullableColumns={}",
                expected.size(), requiredTables.size(), CRITICAL_COLUMNS.size(), CRITICAL_INDEXES.size(),
                CRITICAL_NULLABLE_COLUMNS.size());
    }

    CriticalSchemaShape inspectCriticalSchemaShape() {
        Set<String> actualColumns = normalizeSchemaObjectNames(jdbcTemplate.queryForList(
                "SELECT CONCAT(LOWER(table_name), '.', LOWER(column_name)) "
                        + "FROM information_schema.columns WHERE table_schema=DATABASE()",
                String.class));
        Set<String> actualIndexes = normalizeSchemaObjectNames(jdbcTemplate.queryForList(
                "SELECT DISTINCT CONCAT(LOWER(table_name), '.', LOWER(index_name)) "
                        + "FROM information_schema.statistics WHERE table_schema=DATABASE()",
                String.class));
        Set<String> actualNullableColumns = normalizeSchemaObjectNames(jdbcTemplate.queryForList(
                "SELECT CONCAT(LOWER(table_name), '.', LOWER(column_name)) "
                        + "FROM information_schema.columns WHERE table_schema=DATABASE() AND is_nullable='YES'",
                String.class));
        Set<String> missingColumns = new TreeSet<>(CRITICAL_COLUMNS);
        missingColumns.removeAll(actualColumns);
        Set<String> missingIndexes = new TreeSet<>(CRITICAL_INDEXES);
        missingIndexes.removeAll(actualIndexes);
        Set<String> missingNullableColumns = new TreeSet<>(CRITICAL_NULLABLE_COLUMNS);
        missingNullableColumns.removeAll(actualNullableColumns);
        return new CriticalSchemaShape(missingColumns, missingIndexes, missingNullableColumns);
    }

    private static Set<String> normalizeSchemaObjectNames(List<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.toLowerCase(java.util.Locale.ROOT));
            }
        }
        return normalized;
    }

    Map<String, MigrationContract> loadMigrationContracts() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(MIGRATION_PATTERN);
            if (resources.length == 0) {
                throw new IllegalStateException("No packaged database migrations were found");
            }
            Arrays.sort(resources, (left, right) -> String.valueOf(left.getFilename())
                    .compareTo(String.valueOf(right.getFilename())));
            Map<String, MigrationContract> result = new LinkedHashMap<>();
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || result.containsKey(filename)) {
                    throw new IllegalStateException("Invalid or duplicate packaged migration: " + filename);
                }
                byte[] bytes;
                try (var input = resource.getInputStream()) {
                    bytes = input.readAllBytes();
                }
                result.put(filename, new MigrationContract(
                        sha256(bytes),
                        extractCreatedTables(new String(bytes, StandardCharsets.UTF_8))));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read packaged database migrations", e);
        }
    }

    private Map<String, String> loadAppliedMigrations() {
        try {
            return jdbcTemplate.query(
                    "SELECT migration_name, checksum_sha256 FROM schema_migration ORDER BY installed_rank",
                    resultSet -> {
                        Map<String, String> rows = new HashMap<>();
                        while (resultSet.next()) {
                            rows.put(resultSet.getString(1), resultSet.getString(2));
                        }
                        return rows;
                    });
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Database migration ledger is unavailable. Run the controlled database migration command before startup.",
                    e);
        }
    }

    static Set<String> extractCreatedTables(String sql) {
        Set<String> tables = new TreeSet<>();
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sql);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    record MigrationContract(String checksum, Set<String> createdTables) {
    }

    record CriticalSchemaShape(Set<String> missingColumns, Set<String> missingIndexes,
                               Set<String> missingNullableColumns) {
    }
}
