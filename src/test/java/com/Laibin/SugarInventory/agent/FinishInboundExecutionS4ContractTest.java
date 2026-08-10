package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinishInboundExecutionS4ContractTest {

    @Test
    void isolatedUatProvesDefaultDenyAuditRollbackRecoveryAndStateInvalidation() throws Exception {
        String script = Files.readString(Path.of("scripts", "verify-finish-inbound-s1.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script).contains(
                "VerifyS4AuditRollback",
                "VerifyS4StateInvalidation",
                "S4PermissionDeniedBeforeGrant",
                "CREATE TRIGGER",
                "NEW.event_type='EXECUTION_SUCCEEDED'",
                "FAILED_RETRYABLE",
                "S4RetryAfterAuditRecovery",
                "FAILED_FINAL",
                "S4StateInvalidation");
        assertThat(script).contains(
                "Dedicated Agent execute permission is already assigned to ADMIN",
                "S3 controlled endpoint did not reject the default-deny account");
    }

    @Test
    void releaseCandidateRemainsDefaultOffAndOutsideMcpToolRouting() throws Exception {
        String controller = Files.readString(Path.of("src", "main", "java", "com", "Laibin",
                "SugarInventory", "agent", "controller", "FinishInboundExecutionS3Controller.java"),
                StandardCharsets.UTF_8);

        assertThat(controller).contains(
                "agent.finish-inbound.s3-execute-enabled",
                "havingValue = \"true\"",
                "hasAuthority('agent:finish-inbound:execute')");
        assertThat(McpInternalAgentToolGatewayService.allowedTools())
                .doesNotContain("execute_finish_inbound_task")
                .noneMatch(name -> name.startsWith("execute_"));
    }

    @Test
    void browserFixtureIsLocalExplicitlyPermissionedAndFullyCleanable() throws Exception {
        String script = Files.readString(Path.of("scripts",
                "prepare-finish-inbound-s4-browser-uat.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains(
                "Refusing non-local database host",
                "Dedicated Agent execute permission must be unassigned",
                "permissionLinkInserted",
                "CleanupManifest",
                "DELETE FROM agent_finish_inbound_execution_audit",
                "DELETE FROM stock_movement_event",
                "DELETE FROM role_permission");
        assertThat(script).doesNotContain("executionToken", "idempotencyKey");
    }
}
