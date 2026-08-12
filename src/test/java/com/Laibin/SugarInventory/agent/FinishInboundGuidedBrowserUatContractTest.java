package com.Laibin.SugarInventory.agent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FinishInboundGuidedBrowserUatContractTest {

    @Test
    void fixtureCreatesExactlyTwoFreeFixedCodesAndNeverGrantsAgentExecute() throws Exception {
        String script = Files.readString(Path.of("scripts",
                "prepare-finish-inbound-guided-browser-uat.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains(
                "Refusing non-local database host",
                "warehouse_name='3号库位'",
                "'FREE',NULL,$productId,1",
                "for ($index = 0; $index -lt 2; $index++)",
                "Dedicated Agent execute permission must remain unassigned",
                "unexpectedly has Agent execute permission",
                "Run the fixture script from the project root");
        assertThat(script).doesNotContain(
                "INSERT INTO role_permission",
                "'agent:use'");
    }

    @Test
    void verificationRequiresBothPreviewLayersAndZeroBusinessWrites() throws Exception {
        String script = Files.readString(Path.of("scripts",
                "prepare-finish-inbound-guided-browser-uat.ps1"), StandardCharsets.UTF_8);

        assertThat(script).contains(
                "FixedQrPendingTasks",
                "TaskTransitionPreviews",
                "ExactExecutionPreviews",
                "InventoryWrites",
                "BindingAuditFlows",
                "UnexpectedFlowWrites",
                "StockMovementWrites",
                "ExecuteToolCalls",
                "ExecutionRequests",
                "DELETE FROM agent_task_transition_preview",
                "DELETE FROM agent_finish_inbound_execution_preview");
        assertThat(script).contains(
                "operation_type='FINISH_BIND'",
                "f.remark='固定产品二维码打印并启用'",
                "t.task_type='FINISH_IN'");
    }
}
