package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyMetricValueVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductLineVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportGroupVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportSectionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDailyReportXlsxExporterTest {

    private final ProductionDailyReportXlsxExporter exporter = new ProductionDailyReportXlsxExporter();

    @Test
    void exportUsesTemplateLayoutWorkshopWordingAndEditableProductRows() throws Exception {
        ProductionDailyReportVO report = reportFixture();

        byte[] bytes = exporter.export(report);

        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            XSSFSheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("生产日报");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("来冰公司20260825生产日报表");
            assertThat(cellTexts(sheet)).contains(
                    "车间", "多晶车间", "红糖车间", "单晶车间", "全厂汇总",
                    "测试冰糖产品", "件", "冰糖结晶率", "总用糖量（折白糖）",
                    "月累计(件)", "月累计(吨)", "年累计(吨)",
                    "制表人：测试制表人", "制表日期：2026-08-26");
            assertThat(cellTexts(sheet)).doesNotContain("部门", "多晶部门", "红糖部门", "单晶部门");
            assertThat(cellTexts(sheet)).doesNotContain("袋");
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(sheet.getPrintSetup().getPaperSize()).isEqualTo(PrintSetup.A4_PAPERSIZE);
            assertThat(allCells(sheet)).noneMatch(cell -> cell.getCellType() == CellType.FORMULA);
            assertThat(allCells(sheet)).anyMatch(cell -> cell.getCellType() == CellType.NUMERIC
                    && cell.getNumericCellValue() == 12.5d);
        }
    }

    private ProductionDailyReportVO reportFixture() {
        ProductionDailyReportVO report = new ProductionDailyReportVO();
        report.setReportDate(LocalDate.of(2026, 8, 25));
        report.setPreparedDate(LocalDate.of(2026, 8, 26));
        report.setPreparedByName("测试制表人");
        report.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);

        List<ProductionDailyReportSectionVO> sections = new ArrayList<>();
        for (ProductionDailyReportCatalog.SectionDefinition definition : ProductionDailyReportCatalog.sections()) {
            ProductionDailyReportSectionVO section = new ProductionDailyReportSectionVO();
            section.setDepartmentCode(definition.code());
            section.setDepartmentName(definition.name());
            section.setStatus(ProductionDailyReportCatalog.STATUS_DRAFT);
            for (ProductionDailyReportCatalog.GroupDefinition groupDefinition : definition.groups()) {
                ProductionDailyReportGroupVO group = new ProductionDailyReportGroupVO();
                group.setGroupCode(groupDefinition.code());
                group.setGroupName(groupDefinition.name());
                group.setProductGroup(groupDefinition.productGroup());
                group.setAllowedProductStatuses(groupDefinition.allowedProductStatuses());
                for (ProductionDailyReportCatalog.MetricDefinition metricDefinition : groupDefinition.metrics()) {
                    ProductionDailyMetricValueVO metric = new ProductionDailyMetricValueVO();
                    metric.setMetricCode(metricDefinition.code());
                    metric.setMetricName(metricDefinition.name());
                    metric.setUnit(metricDefinition.unit());
                    metric.setPosition(metricDefinition.position());
                    metric.setDisplayOrder(metricDefinition.displayOrder());
                    group.getMetricValues().add(metric);
                }
                if ("POLYCRYSTAL".equals(definition.code()) && "SEMI_PRODUCT".equals(groupDefinition.code())) {
                    ProductionDailyProductLineVO product = new ProductionDailyProductLineVO();
                    product.setProductId(1);
                    product.setProductName("测试冰糖产品");
                    product.setProductStatus("半成品");
                    product.setProductType("冰糖");
                    product.setUnit("袋");
                    product.setDailyActual(new BigDecimal("12.5"));
                    product.setDisplayOrder(1);
                    group.getProductLines().add(product);
                }
                section.getGroups().add(group);
            }
            sections.add(section);
        }
        report.setSections(sections);
        return report;
    }

    private List<String> cellTexts(XSSFSheet sheet) {
        return allCells(sheet).stream()
                .filter(cell -> cell.getCellType() == CellType.STRING)
                .map(Cell::getStringCellValue)
                .toList();
    }

    private List<Cell> allCells(XSSFSheet sheet) {
        List<Cell> cells = new ArrayList<>();
        for (Row row : sheet) {
            row.forEach(cells::add);
        }
        return cells;
    }
}
