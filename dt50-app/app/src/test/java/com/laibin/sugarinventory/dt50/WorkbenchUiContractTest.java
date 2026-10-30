package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkbenchUiContractTest {

    @Test
    public void workbenchUsesIconsInsteadOfTextAbbreviations() throws IOException {
        String xml = readLayout("activity_workbench.xml");

        for (String icon : List.of(
                "ic_workbench_inbound",
                "ic_workbench_outbound",
                "ic_workbench_transfer",
                "ic_workbench_warehouse",
                "ic_workbench_product",
                "ic_workbench_assay",
                "ic_workbench_history")) {
            assertTrue("missing workbench icon: " + icon, xml.contains("@drawable/" + icon));
        }
        for (String abbreviation : List.of("入", "出", "调", "仓", "货", "验", "录")) {
            assertFalse("text abbreviation must not be used as an icon: " + abbreviation,
                    xml.contains("android:text=\"" + abbreviation + "\""));
        }
    }

    @Test
    public void scanPagesDoNotShowRedundantReadySubtitle() throws IOException {
        assertFalse(readLayout("activity_main.xml").contains("可以开始扫码"));
        assertFalse(readLayout("activity_pallet_operation.xml").contains("可以开始扫码"));
    }

    @Test
    public void workbenchIncludesNativeDailyReportEntry() throws IOException {
        String xml = readLayout("activity_workbench.xml");
        assertTrue(xml.contains("@+id/dailyReportButton"));
        assertTrue(xml.contains("@drawable/ic_daily_calendar"));
        assertTrue(readLayout("activity_production_daily_report.xml").contains("@+id/dailyTabs"));
    }

    private String readLayout(String fileName) throws IOException {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate : List.of(
                base.resolve("src/main/res/layout").resolve(fileName),
                base.resolve("app/src/main/res/layout").resolve(fileName))) {
            if (Files.isRegularFile(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("layout not found: " + fileName + " from " + base);
    }
}
