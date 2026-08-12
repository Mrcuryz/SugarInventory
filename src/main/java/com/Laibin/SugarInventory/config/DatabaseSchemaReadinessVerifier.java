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

        if (!missingMigrations.isEmpty() || !checksumMismatches.isEmpty() || !missingTables.isEmpty()) {
            throw new IllegalStateException("Database schema is not ready; missingMigrations=" + missingMigrations
                    + "; checksumMismatches=" + checksumMismatches
                    + "; missingTables=" + missingTables
                    + ". Run the controlled database migration command before application startup.");
        }
        log.info("Database schema verification passed: migrations={}, requiredTables={}",
                expected.size(), requiredTables.size());
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
}
