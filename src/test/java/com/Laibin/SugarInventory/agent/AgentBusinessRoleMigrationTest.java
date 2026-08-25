package com.Laibin.SugarInventory.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBusinessRoleMigrationTest {

    @Test
    void migrationAddsExplicitAgentUseAndReadOnlyBusinessRoles() throws Exception {
        String migration = Files.readString(
                Path.of("migrations", "2026-08-13-add-agent-use-and-readonly-business-roles.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains(
                "'agent:use'",
                "'WAREHOUSE_MANAGER'",
                "'PRODUCTION_SUPERVISOR'",
                "'QC'",
                "'inventory:view'",
                "'assay:view'",
                "'production:boiling:view'",
                "'production:order:view'",
                "'production:material:view'",
                "'production:output:view'");
        assertThat(migration).doesNotContain(
                "'agent:finish-inbound:execute'",
                "'inventory:inbound'",
                "'inventory:outbound'",
                "'inventory:transfer'",
                "'task:create'",
                "'task:confirm'",
                "'task:cancel'",
                "'production:order:create'",
                "'production:material:pick'",
                "'production:output:create'");

        String compatibilityMigration = Files.readString(
                Path.of("migrations", "2026-08-13-shorten-production-supervisor-role-code.sql"),
                StandardCharsets.UTF_8);
        assertThat(compatibilityMigration).contains(
                "'PRODUCTION_SUPERVISOR'",
                "'PROD_SUPERVISOR'");
    }

    @Test
    void warehouseManagerGetsOnlyTheDedicatedControlledInboundWritePermission() throws Exception {
        String migration = Files.readString(
                Path.of("migrations",
                        "2026-08-13-grant-warehouse-manager-controlled-finish-inbound.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains(
                "'WAREHOUSE_MANAGER'",
                "'agent:finish-inbound:execute'",
                "受控成品入库执行");
        assertThat(migration).doesNotContain(
                "'task:confirm'",
                "'task:create'",
                "'task:cancel'",
                "'inventory:outbound'",
                "'inventory:transfer'",
                "'qrcode:activate'");
    }
}
