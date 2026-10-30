package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ProductionDailyReportDataTest {
    @Test
    public void decimalValidationMatchesDatabasePrecision() {
        assertNull(ProductionDailyReportData.validateNumber(""));
        assertNull(ProductionDailyReportData.validateNumber("0"));
        assertNull(ProductionDailyReportData.validateNumber("-123456789012.123456"));
        assertNotNull(ProductionDailyReportData.validateNumber("1234567890123"));
        assertNotNull(ProductionDailyReportData.validateNumber("0.1234567"));
        assertNotNull(ProductionDailyReportData.validateNumber("abc"));
    }

    @Test
    public void importedProductNameRequiresAnActualProductSelection() {
        ProductionDailyReportData.Row product = new ProductionDailyReportData.Row();
        product.product = true;
        product.name = "待匹配产品";
        product.importedName = "待匹配产品";
        assertTrue(product.empty());
        assertTrue(product.hasContent());

        ProductionDailyReportData.Group group = new ProductionDailyReportData.Group();
        group.name = "半成品";
        group.productGroup = true;
        group.products.add(product);
        ProductionDailyReportData.Section section = new ProductionDailyReportData.Section();
        section.groups.add(group);
        assertTrue(ProductionDailyReportData.validateSection(section).contains("请选择产品"));
        product.productId = 42;
        assertNull(ProductionDailyReportData.validateSection(section));
    }

    @Test
    public void productRowsKeepCatalogBeforeAndAfterOrder() {
        ProductionDailyReportData.Group group = new ProductionDailyReportData.Group();
        group.productGroup = true;
        ProductionDailyReportData.Row before = metric("BEFORE_PRODUCT");
        ProductionDailyReportData.Row after = metric("AFTER_PRODUCT");
        ProductionDailyReportData.Row first = product(1);
        ProductionDailyReportData.Row second = product(2);
        group.metrics.add(before);
        group.metrics.add(after);
        group.products.add(first);
        group.products.add(second);

        assertEquals(List.of(before, first, second, after), group.allRows());
        group.moveProduct(second, -1);
        assertEquals(List.of(before, second, first, after), group.allRows());
        assertEquals(4, group.missingCount());
        first.values[0] = "0";
        assertEquals(3, group.missingCount());
    }

    @Test
    public void localDraftMergesOnlyDirtySectionAndFlagsVersionConflict() {
        ProductionDailyReportData.Report server = new ProductionDailyReportData.Report();
        server.version = 4;
        ProductionDailyReportData.Section serverSection = new ProductionDailyReportData.Section();
        serverSection.code = "POLYCRYSTAL";
        serverSection.version = 3;
        server.sections.add(serverSection);

        ProductionDailyReportData.Report local = new ProductionDailyReportData.Report();
        local.version = 2;
        local.preparedBy = "本机填写";
        local.headerDirty = true;
        ProductionDailyReportData.Section localSection = new ProductionDailyReportData.Section();
        localSection.code = "POLYCRYSTAL";
        localSection.version = 2;
        localSection.dirty = true;
        ProductionDailyReportData.Group localGroup = new ProductionDailyReportData.Group();
        localSection.groups.add(localGroup);
        local.sections.add(localSection);

        ProductionDailyReportData.Report merged = ProductionDailyReportData.mergeUnsaved(server, local);
        assertSame(server, merged);
        assertEquals("本机填写", merged.preparedBy);
        assertTrue(merged.headerConflict);
        assertTrue(merged.section("POLYCRYSTAL").conflict);
        assertSame(localGroup, merged.section("POLYCRYSTAL").groups.get(0));
        assertFalse(localSection.conflict);
    }

    private ProductionDailyReportData.Row metric(String position) {
        ProductionDailyReportData.Row row = new ProductionDailyReportData.Row();
        row.position = position;
        return row;
    }

    private ProductionDailyReportData.Row product(int id) {
        ProductionDailyReportData.Row row = new ProductionDailyReportData.Row();
        row.product = true;
        row.productId = id;
        return row;
    }
}
