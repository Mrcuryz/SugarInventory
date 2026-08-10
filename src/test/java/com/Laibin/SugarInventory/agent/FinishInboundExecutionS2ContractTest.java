package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinishInboundExecutionS2ContractTest {

    @Test
    void controlPlaneMigrationStoresHashesButNeverRawCredentials() throws Exception {
        String migration = Files.readString(Path.of("migrations",
                "2026-08-10-add-agent-finish-inbound-execution-control-plane.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains(
                "agent_finish_inbound_execution_confirmation",
                "agent_finish_inbound_execution_request",
                "agent_finish_inbound_execution_audit",
                "token_sha256",
                "idempotency_key_sha256",
                "preview_state_digest",
                "attempt_count");
        assertThat(migration).doesNotContain(
                "execution_token VARCHAR", "idempotency_key VARCHAR");
    }

    @Test
    void s2NoopIsExplicitlyGatedAndDoesNotOpenAnMcpExecuteTool() throws Exception {
        String controller = Files.readString(Path.of("src", "main", "java", "com", "Laibin",
                "SugarInventory", "agent", "controller", "FinishInboundExecutionS2NoopController.java"),
                StandardCharsets.UTF_8);
        String adapter = Files.readString(Path.of("src", "main", "java", "com", "Laibin",
                "SugarInventory", "agent", "service", "DefaultFinishInboundExecutionNoopAdapter.java"),
                StandardCharsets.UTF_8);

        assertThat(controller).contains(
                "agent.finish-inbound.s2-noop-enabled", "havingValue = \"true\"");
        assertThat(adapter).contains("S2_NOOP_VALIDATED", "new AdapterResult").doesNotContain(
                "InventoryService", "InStockService", "PalletTaskService", "confirmPalletIn");
        assertThat(McpInternalAgentToolGatewayService.allowedTools())
                .doesNotContain("execute_finish_inbound_task")
                .noneMatch(name -> name.startsWith("execute_"));
    }
}
