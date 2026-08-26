package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyMetricValueVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductLineVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportGroupVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportSectionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportCatalog;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionDailyReportXlsxImporterTest {

    private final ProductionDailyReportXlsxImporter importer = new ProductionDailyReportXlsxImporter();
    private final ProductionDailyReportXlsxExporter exporter = new ProductionDailyReportXlsxExporter();

    @Test
    void importsAWorkbookExportedByTheSystemWithoutLosingDynamicRows() {
        byte[] workbook = exporter.export(reportFixture());

        ProductionDailyReportXlsxImporter.ParsedReport result = importer.parse(
                new ByteArrayInputStream(workbook));

        assertThat(result.reportDate()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(result.preparedDate()).isEqualTo(LocalDate.of(2026, 8, 28));
        assertThat(result.preparedByName()).isEqualTo("导入测试制表人");
        assertThat(result.sections()).extracting(ProductionDailyReportXlsxImporter.ParsedSection::departmentCode)
                .containsExactly("POLYCRYSTAL", "BROWN_SUGAR", "MONOCRYSTAL", "FACTORY_SUMMARY");

        ProductionDailyReportXlsxImporter.ParsedSection poly = section(result, "POLYCRYSTAL");
        assertThat(poly.metricValues()).hasSize(20);
        assertThat(poly.metricValues())
                .filteredOn(metric -> "POLY_SUGAR_OIL".equals(metric.getMetricCode()))
                .singleElement()
                .satisfies(metric -> assertThat(metric.getDailyActual()).isEqualByComparingTo("7119"));
        assertThat(poly.productLines())
                .extracting(ProductionDailyReportXlsxImporter.ParsedProductLine::productName)
                .containsExactly("黄中冰", "黄小颗粒", "黄冰糖（袋）");
        assertThat(poly.productLines().get(1).displayOrder()).isEqualTo(2);
        assertThat(poly.productLines().get(1).monthQuantity()).isEqualByComparingTo("202");
    }

    @Test
    void readsTheProvidedLegacyTemplateIncludingItsTwoColumnLayoutAndMetricAlias() throws Exception {
        ClassPathResource template = new ClassPathResource("templates/production-daily-report-template.xlsx");
        try (InputStream input = template.getInputStream()) {
            ProductionDailyReportXlsxImporter.ParsedReport result = importer.parse(input);

            assertThat(result.reportDate()).isEqualTo(LocalDate.of(2026, 8, 20));
            assertThat(result.preparedDate()).isEqualTo(LocalDate.of(2026, 8, 21));
            assertThat(result.preparedByName()).isEqualTo("郑盛果");
            assertThat(result.sections()).hasSize(4);
            assertThat(section(result, "POLYCRYSTAL").metricValues())
                    .filteredOn(metric -> "POLY_CRYSTALLIZATION_RATE".equals(metric.getMetricCode()))
                    .singleElement();
            assertThat(section(result, "POLYCRYSTAL").productLines())
                    .extracting(ProductionDailyReportXlsxImporter.ParsedProductLine::productName)
                    .contains("40kg纯白中冰", "15kg浮冰");
            assertThat(section(result, "FACTORY_SUMMARY").metricValues())
                    .filteredOn(metric -> "FACTORY_TOTAL_OUTPUT".equals(metric.getMetricCode()))
                    .singleElement()
                    .satisfies(metric -> assertThat(metric.getDailyActual()).isEqualByComparingTo("4170"));
        }
    }

    @Test
    void rejectsAWorkbookWithoutTheProductionDailyReportTitle() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("错误模板").createRow(0).createCell(0).setCellValue("普通表格");
            workbook.write(output);
            bytes = output.toByteArray();
        }

        assertThatThrownBy(() -> importer.parse(new ByteArrayInputStream(bytes)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产日报表");
    }

    private ProductionDailyReportXlsxImporter.ParsedSection section(
            ProductionDailyReportXlsxImporter.ParsedReport report, String code) {
        return report.sections().stream()
                .filter(section -> code.equals(section.departmentCode()))
                .findFirst()
                .orElseThrow();
    }

    private ProductionDailyReportVO reportFixture() {
        ProductionDailyReportVO report = new ProductionDailyReportVO();
        report.setReportDate(LocalDate.of(2026, 8, 27));
        report.setPreparedDate(LocalDate.of(2026, 8, 28));
        report.setPreparedByName("导入测试制表人");
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
                    if ("POLY_SUGAR_OIL".equals(metricDefinition.code())) {
                        metric.setDailyActual(new BigDecimal("7119"));
                    }
                    group.getMetricValues().add(metric);
                }
                if ("POLYCRYSTAL".equals(definition.code()) && "SEMI_PRODUCT".equals(groupDefinition.code())) {
                    group.getProductLines().add(product(47, "黄中冰", "半成品", "11", "101", 1));
                    group.getProductLines().add(product(50, "黄小颗粒", "半成品", "22", "202", 2));
                }
                if ("POLYCRYSTAL".equals(definition.code()) && "DIRECT_PRODUCT".equals(groupDefinition.code())) {
                    group.getProductLines().add(product(84, "黄冰糖（袋）", "成品", "33", "303", 1));
                }
                section.getGroups().add(group);
            }
            sections.add(section);
        }
        report.setSections(sections);
        return report;
    }

    private ProductionDailyProductLineVO product(Integer id, String name, String status,
                                                  String dailyActual, String monthQuantity, int order) {
        ProductionDailyProductLineVO product = new ProductionDailyProductLineVO();
        product.setProductId(id);
        product.setProductName(name);
        product.setProductStatus(status);
        product.setProductType("黄冰糖");
        product.setUnit("件");
        product.setDailyActual(new BigDecimal(dailyActual));
        product.setConvertedTons(new BigDecimal("0.88"));
        product.setMonthQuantity(new BigDecimal(monthQuantity));
        product.setMonthTons(new BigDecimal("8.08"));
        product.setYearTons(new BigDecimal("80.8"));
        product.setRemark("导入回填-" + name);
        product.setDisplayOrder(order);
        return product;
    }
}
