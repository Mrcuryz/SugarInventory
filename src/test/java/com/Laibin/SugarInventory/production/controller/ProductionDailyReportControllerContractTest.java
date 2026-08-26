package com.Laibin.SugarInventory.production.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDailyReportControllerContractTest {

    @Test
    void endpointsUseTheThreeApprovedOperationPermissions() {
        assertPermission("pageReports", "hasAuthority('production:daily-report:view')");
        assertPermission("getReport", "hasAuthority('production:daily-report:view')");
        assertPermission("listProductOptions",
                "hasAnyAuthority('production:daily-report:view', 'production:daily-report:edit')");
        assertPermission("saveHeader", "hasAuthority('production:daily-report:edit')");
        assertPermission("saveSection", "hasAuthority('production:daily-report:edit')");
        assertPermission("submitSection", "hasAuthority('production:daily-report:edit')");
        assertPermission("submitReport", "hasAuthority('production:daily-report:edit')");
        assertPermission("importReport", "hasAuthority('production:daily-report:edit')");
        assertPermission("confirmImport", "hasAuthority('production:daily-report:edit')");
        assertPermission("exportReport", "hasAuthority('production:daily-report:export')");
    }

    @Test
    void migrationCreatesDailyReportTablesAndRegistersPermissions() throws Exception {
        String migration = Files.readString(
                Path.of("migrations", "2026-08-26-add-production-daily-report.sql"),
                StandardCharsets.UTF_8);

        assertThat(migration).contains(
                "production_daily_report",
                "production_daily_report_section",
                "production_daily_metric_value",
                "production_daily_product_line",
                "production:daily-report:view",
                "production:daily-report:edit",
                "production:daily-report:export",
                "r.`role_code` = 'ADMIN'");
    }

    private void assertPermission(String methodName, String expression) {
        Method method = Arrays.stream(ProductionDailyReportController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("ProductionDailyReportController#%s permission", methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }
}
