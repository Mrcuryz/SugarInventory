package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinishInboundExecutionS3ContractTest {

    @Test
    void dedicatedPermissionIsRegisteredWithoutAnyDefaultRoleAssignment() throws Exception {
        String migration = Files.readString(Path.of("migrations",
                "2026-08-10-add-agent-finish-inbound-execute-permission.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains("agent:finish-inbound:execute", "INSERT INTO `permission`");
        assertThat(migration).doesNotContain("INSERT INTO `role_permission`", "ADMIN", "STAFF", "QC");
    }

    @Test
    void realExecutionIsDefaultOffPermissionGuardedAndNotAnMcpTool() throws Exception {
        String controller = Files.readString(Path.of("src", "main", "java", "com", "Laibin",
                "SugarInventory", "agent", "controller", "FinishInboundExecutionS3Controller.java"),
                StandardCharsets.UTF_8);
        String ui = Files.readString(Path.of("webpage", "src", "components", "agent",
                "FinishInboundExecutionConfirmDialog.vue"), StandardCharsets.UTF_8);

        assertThat(controller).contains(
                "agent.finish-inbound.s3-execute-enabled",
                "havingValue = \"true\"",
                "hasAuthority('agent:finish-inbound:execute')",
                "confirmForDomainExecution",
                "confirm-and-execute");
        assertThat(controller).doesNotContain(
                "hasAuthority('task:confirm')",
                "AgentAccessPolicy.requireAdmin");
        assertThat(ui).doesNotContain("executionToken", "idempotencyKey", "confirmationRef");
        assertThat(McpInternalAgentToolGatewayService.allowedTools())
                .doesNotContain("execute_finish_inbound_task")
                .noneMatch(name -> name.startsWith("execute_"));
    }
}
