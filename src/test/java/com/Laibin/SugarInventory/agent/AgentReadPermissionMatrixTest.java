package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.controller.AssayController;
import com.Laibin.SugarInventory.controller.InventoryAgentReadController;
import com.Laibin.SugarInventory.controller.LogisticsAgentReadController;
import com.Laibin.SugarInventory.controller.PalletCodeController;
import com.Laibin.SugarInventory.controller.WarehouseAgentReadController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AgentReadPermissionMatrixTest {

    @Test
    void agentReadEndpointsUseDedicatedReadAuthorities() {
        assertPermission(InventoryAgentReadController.class, "queryInventoryLedger", "hasAuthority('inventory:view')");
        assertPermission(InventoryAgentReadController.class, "queryPreparePoolBalance", "hasAuthority('inventory:view')");
        assertPermission(WarehouseAgentReadController.class, "queryCapacityDistribution", "hasAuthority('warehouse:view')");
        assertPermission(WarehouseAgentReadController.class, "queryRecentOperations", "hasAuthority('warehouse:view')");
        assertPermission(WarehouseAgentReadController.class, "queryMixedStorageFacts", "hasAuthority('warehouse:view')");
        assertPermission(LogisticsAgentReadController.class, "queryPalletTasks", "hasAuthority('task:view')");
        assertPermission(LogisticsAgentReadController.class, "queryStockDocuments", "hasAuthority('document:view')");
        assertPermission(LogisticsAgentReadController.class, "queryAutoInboundBatches", "hasAuthority('task:view')");
        assertPermission(LogisticsAgentReadController.class, "getAutoInboundBatchDetail", "hasAuthority('task:view')");
    }

    @Test
    void assayAndPalletMcpReadPathsDoNotReuseWritePermissions() {
        for (String method : new String[]{
                "queryAssays", "queryAssayRecords", "getAssayReportDetail", "queryAssayAbnormalities",
                "queryProductsWithoutRecentAssay", "queryAssayStandardCoverage", "getAssayById",
                "getAssayByProductDate"}) {
            assertPermission(AssayController.class, method, "hasAuthority('assay:view')");
        }
        for (String method : new String[]{
                "pageFlowCycles", "listFlowsByCycle", "parse", "queryQrCodeLifecycle",
                "queryPalletFlowRecords", "queryPrintedNotInboundCodes", "queryPalletAnomalies",
                "queryQrBatchInboundCompletion", "getAssay", "getInventory"}) {
            assertPermission(PalletCodeController.class, method, "hasAuthority('qrcode:view')");
        }
    }

    private void assertPermission(Class<?> controller, String methodName, String expression) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(controller.getSimpleName() + "." + methodName).isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
        assertThat(annotation.value()).doesNotContain("isAuthenticated", "quality:test");
    }
}
