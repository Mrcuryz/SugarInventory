package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FinishInboundL3ReadinessContractTest {

    @Test
    void keepsAllExecuteToolsClosedWhileFinishInboundGateIsNoGo() throws Exception {
        assertThat(McpInternalAgentToolGatewayService.allowedTools())
                .noneMatch(name -> name.startsWith("execute_"))
                .doesNotContain("execute_finish_inbound_task");

        String gate = Files.readString(Path.of(
                "docs", "agent", "finish-inbound-l3-gate.yaml"), StandardCharsets.UTF_8);
        assertThat(gate).contains(
                "overall_status: NO_GO",
                "name: execute_finish_inbound_task",
                "registered: false",
                "enabled: false",
                "model_generated_confirmation: FORBIDDEN");
    }

    @Test
    void distinguishesTaskEligibilityPreviewFromExactInboundExecutionInput() {
        assertThat(fieldNames(TaskTransitionPreviewDTO.class))
                .containsExactlyInAnyOrder("previewVersion", "transition", "palletCodes")
                .doesNotContain("warehouseName", "entryDate", "side", "quantity", "unit", "remark");

        assertThat(fieldNames(ConfirmPalletInItemDTO.class)).contains(
                "code", "warehouseName", "entryDate", "side",
                "rowNumber", "layer", "quantity", "unit", "remark");
    }

    @Test
    void recognizesCompletedS0BusinessHardeningWithoutOpeningL3Execution() throws Exception {
        Field items = ConfirmPalletInBatchDTO.class.getDeclaredField("items");
        assertThat(items.getAnnotation(Size.class)).isNotNull();
        assertThat(items.getAnnotation(Size.class).max()).isEqualTo(20);

        String gate = Files.readString(Path.of(
                "docs", "agent", "finish-inbound-l3-gate.yaml"), StandardCharsets.UTF_8);
        assertThat(gate).contains(
                "business_hardening_status: GO",
                "batch_limit_dedup_sort:",
                "batch_over_20_is_rejected: PASS",
                "concurrent_confirm_vs_cancel_has_one_deterministic_winner: PASS_LOCAL_MYSQL_5_ROUNDS",
                "batch_failure_rolls_back_all_business_changes: PASS_LOCAL_MYSQL",
                "status: LOCAL_MYSQL_UAT_GO",
                "execution_implementation_status: NO_GO");
    }

    @Test
    void previewPersistenceDoesNotPretendToBeExecutionInfrastructure() throws Exception {
        String migration = Files.readString(Path.of(
                "migrations", "2026-08-05-add-agent-task-transition-preview.sql"), StandardCharsets.UTF_8);

        assertThat(migration).contains("CREATE TABLE IF NOT EXISTS agent_task_transition_preview");
        assertThat(migration).doesNotContain(
                "execution_token", "idempotency_key", "confirmation_ref", "confirmed_at");
    }

    private static String[] fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .toArray(String[]::new);
    }
}
