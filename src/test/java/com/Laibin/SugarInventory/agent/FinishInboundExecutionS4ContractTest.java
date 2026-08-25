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
                "[string]::IsNullOrWhiteSpace($PSScriptRoot)",
                "Unable to resolve project root",
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
                "[string]::IsNullOrWhiteSpace($PSScriptRoot)",
                "Unable to resolve project root",
                "[string]$LoginPassword = \"lbsp-isolated-uat\"",
                "Dedicated Agent execute permission must be unassigned",
                "permissionLinkInserted",
                "CleanupManifest",
                "DELETE FROM agent_finish_inbound_execution_audit",
                "DELETE FROM stock_movement_event",
                "DELETE FROM role_permission");
        assertThat(script).doesNotContain("executionToken", "idempotencyKey");
    }

    @Test
    void isolatedLauncherSupportsSafeScriptBlockInvocationFromProjectRoot() throws Exception {
        String script = Files.readString(Path.of("scripts",
                "start-isolated-agent-uat.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains(
                "[string]::IsNullOrWhiteSpace($PSScriptRoot)",
                "(Get-Location).Path",
                "Test-Path -LiteralPath (Join-Path $projectRoot 'pom.xml')",
                "Unable to resolve project root",
                "[string]$PythonExecutable",
                "[string]$PythonPath",
                "[string]$LoginPassword = \"lbsp-isolated-uat\"",
                "pass -PythonExecutable",
                "$env:PYTHONPATH = $PythonPath",
                "$env:WEB_LOGIN_PASSWORD = $LoginPassword",
                "[switch]$EnableRag",
                "[switch]$RequireRag",
                "[switch]$EnableDatabaseSchemaVerification",
                "$env:AGENT_RAG_REQUIRED = if ($RequireRag)",
                "$env:DATABASE_SCHEMA_VERIFICATION_ENABLED = if ($EnableDatabaseSchemaVerification)");
    }

    @Test
    void credentialRecoveryFixtureCoversRealCrossUserRevocationExpiryAndSessionRevocation() throws Exception {
        String script = Files.readString(Path.of("scripts",
                "verify-finish-inbound-s4-credential-recovery-browser.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains(
                "Refusing non-local application host",
                "Refusing non-local database host",
                "Cross-user replay",
                "Revoked confirmation",
                "Expired confirmation",
                "Revoked session",
                "Permission rollback",
                "ExecutionRequests=0",
                "BusinessWrites=0",
                "CredentialsReturnedToOutput=$false",
                "DELETE rp FROM role_permission");
        assertThat(script).doesNotContain("Write-Output $executionToken", "Write-Output $idempotencyKey");
    }
}
