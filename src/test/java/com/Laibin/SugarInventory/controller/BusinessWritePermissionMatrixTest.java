package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.agent.controller.AgentReviewController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessWritePermissionMatrixTest {

    @Test
    void legacyInventoryAndAutoInboundWritesUseExplicitOperationPermissions() {
        assertPermission(InStockController.class, "stockIn", "hasAuthority('inventory:inbound')");
        assertPermission(OutStockController.class, "createOutStock", "hasAuthority('inventory:outbound')");
        assertPermission(OutStockController.class, "createStackOutStock", "hasAuthority('inventory:outbound')");
        assertPermission(OutStockController.class, "transferOut", "hasAuthority('inventory:transfer')");
        assertPermission(SemiProductRecordController.class, "addSemiProductRecord", "hasAuthority('inventory:inbound')");
        assertPermission(SemiProductRecordController.class, "stackModeInStock", "hasAuthority('inventory:inbound')");

        assertClassPermission(AutoInboundController.class, "hasAuthority('task:view')");
        assertPermission(AutoInboundController.class, "parse", "hasAuthority('task:create')");
        assertPermission(AutoInboundController.class, "confirm", "hasAuthority('task:confirm')");
    }

    @Test
    void masterDataAndQualityWritesUseTheirOwnPermissionFamilies() {
        assertClassPermission(WarehouseController.class, "hasAuthority('warehouse:view')");
        assertPermission(WarehouseController.class, "createWarehouse", "hasAuthority('warehouse:create')");
        assertPermission(WarehouseController.class, "updateWarehouse", "hasAuthority('warehouse:update')");
        assertPermission(WarehouseController.class, "deleteWarehouse", "hasAuthority('warehouse:delete')");
        assertPermission(WarehouseController.class, "updateWarehouseToMaintain", "hasAuthority('warehouse:status')");

        assertClassPermission(ScreenMeshController.class, "hasAuthority('screen_mesh:view')");
        assertPermission(ScreenMeshController.class, "addScreenMesh", "hasAuthority('screen_mesh:create')");
        assertPermission(ScreenMeshController.class, "updateScreenMesh", "hasAuthority('screen_mesh:update')");
        assertPermission(ScreenMeshController.class, "deleteScreenMesh", "hasAuthority('screen_mesh:delete')");

        assertClassPermission(AssayGroupController.class, "hasAuthority('assay_group:view')");
        assertPermission(AssayGroupController.class, "addAssays", "hasAuthority('assay_group:create')");
        assertPermission(AssayGroupController.class, "updateAssayGroup", "hasAuthority('assay_group:update')");
        assertPermission(AssayGroupController.class, "deleteAssay", "hasAuthority('assay_group:delete')");

        assertClassPermission(QualityStandardController.class, "hasAuthority('quality_standard:view')");
        assertPermission(QualityStandardController.class, "addQualityStandard", "hasAuthority('quality_standard:create')");
        assertPermission(QualityStandardController.class, "updateQualityStandard", "hasAuthority('quality_standard:update')");
        assertPermission(QualityStandardController.class, "deleteQualityStandard", "hasAuthority('quality_standard:delete')");
        assertPermission(QualityStandardController.class, "forceDeleteQualityStandard", "hasAuthority('quality_standard:delete')");

        assertClassPermission(ProductQualityStandardRelationController.class, "hasAuthority('quality_standard:view')");
        assertPermission(ProductQualityStandardRelationController.class, "bindRelation", "hasAuthority('quality_standard:bind_product')");
        assertPermission(ProductQualityStandardRelationController.class, "updateRelation", "hasAuthority('quality_standard:bind_product')");
        assertPermission(ProductQualityStandardRelationController.class, "setDefault", "hasAuthority('quality_standard:bind_product')");
        assertPermission(ProductQualityStandardRelationController.class, "deleteRelation", "hasAuthority('quality_standard:bind_product')");
    }

    @Test
    void palletAdministrationAndTaskWritesAreSeparated() {
        assertClassPermission(PalletCodeController.class, "hasAuthority('qrcode:view')");
        assertPermission(PalletCodeController.class, "generate", "hasAuthority('qrcode:generate')");
        assertPermission(PalletCodeController.class, "bindFixedProduct", "hasAuthority('qrcode:bind_fixed_product')");
        assertPermission(PalletCodeController.class, "invalidateCodes", "hasAuthority('qrcode:invalidate')");
        assertPermission(PalletCodeController.class, "restoreInvalidCodes", "hasAuthority('qrcode:invalidate')");
        assertPermission(PalletCodeController.class, "deleteFlows", "hasAuthority('qrcode:flow_delete')");
        assertPermission(PalletCodeController.class, "bind", "hasAuthority('task:create')");
        assertPermission(PalletCodeController.class, "bindSemiItems", "hasAuthority('task:create')");
        assertPermission(PalletCodeController.class, "createWarehouseMapTasks", "hasAuthority('task:create')");
        assertPermission(PalletCodeController.class, "createWarehouseMapSlotInbound",
                "hasAuthority('task:create') and hasAuthority('task:confirm')");
    }

    @Test
    void reviewUpdateDoesNotReuseReadPermission() {
        assertPermission(AgentReviewController.class, "pageReviews", "hasAuthority('agent:review:view')");
        assertPermission(AgentReviewController.class, "getReviewDetail", "hasAuthority('agent:review:view')");
        assertPermission(AgentReviewController.class, "updateReviewStatus", "hasAuthority('agent:review:update')");
    }

    @Test
    void migrationRegistersNewPermissionsWithLeastPrivilegeDefaults() throws Exception {
        String migration = Files.readString(
                Path.of("migrations", "2026-08-12-complete-business-write-permission-matrix.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains(
                "inventory:inbound", "inventory:outbound", "inventory:transfer",
                "warehouse:create", "warehouse:update", "warehouse:delete", "warehouse:status",
                "screen_mesh:create", "screen_mesh:update", "screen_mesh:delete",
                "qrcode:invalidate", "qrcode:flow_delete",
                "r.`role_code` = 'ADMIN'",
                "r.`role_code` = 'STAFF'");
        assertThat(migration).doesNotContain("r.`role_code` = 'QC'");
    }

    @Test
    void permissionMatrixBrowserFixtureUsesUtf8SqlFileInsteadOfCommandLineSql() throws Exception {
        String script = Files.readString(
                Path.of("scripts", "prepare-permission-matrix-browser-uat.ps1"),
                StandardCharsets.UTF_8);

        assertThat(script).contains(
                "[System.Text.UTF8Encoding]::new($false)",
                "--execute=source $sourcePath",
                "Remove-Item -LiteralPath $sqlPath",
                "$viewName = \"PV$($suffix.Substring(0, 4))\"",
                "$adminName = \"PA$($suffix.Substring(4, 4))\"",
                "Local permission-matrix UAT only");
        assertThat(script).doesNotContain("--execute=$Sql");
    }

    private void assertClassPermission(Class<?> controller, String expression) {
        PreAuthorize annotation = controller.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s class permission", controller.getSimpleName())
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }

    private void assertPermission(Class<?> controller, String methodName, String expression) {
        Method method = Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s#%s permission", controller.getSimpleName(), methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
}
