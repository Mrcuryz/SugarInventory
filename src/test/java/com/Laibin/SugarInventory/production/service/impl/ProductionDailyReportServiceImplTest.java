package com.Laibin.SugarInventory.production.service.impl;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyProductLineDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportSectionSaveDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyProductLine;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyReport;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyReportSection;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportImportVO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyMetricValueMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyProductLineMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportSectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductionDailyReportServiceImplTest {

    private ProductionDailyReportMapper reportMapper;
    private ProductionDailyReportSectionMapper sectionMapper;
    private ProductionDailyMetricValueMapper metricValueMapper;
    private ProductionDailyProductLineMapper productLineMapper;
    private ProductMapper productMapper;
    private ProductionDailyReportXlsxImporter xlsxImporter;
    private ProductionDailyReportServiceImpl service;

    @BeforeEach
    void setUp() {
        reportMapper = mock(ProductionDailyReportMapper.class);
        sectionMapper = mock(ProductionDailyReportSectionMapper.class);
        metricValueMapper = mock(ProductionDailyMetricValueMapper.class);
        productLineMapper = mock(ProductionDailyProductLineMapper.class);
        productMapper = mock(ProductMapper.class);
        xlsxImporter = mock(ProductionDailyReportXlsxImporter.class);
        service = new ProductionDailyReportServiceImpl(
                reportMapper,
                sectionMapper,
                metricValueMapper,
                productLineMapper,
                productMapper,
                mock(ProductionDailyReportXlsxExporter.class),
                xlsxImporter);
    }

    @Test
    void missingDateReturnsAnUnpersistedTemplateWithWorkshopNames() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        when(reportMapper.selectByReportDate(date)).thenReturn(null);

        ProductionDailyReportVO result = service.getReport(date);

        assertThat(result.getId()).isNull();
        assertThat(result.getVersion()).isZero();
        assertThat(result.getSections())
                .extracting(section -> section.getDepartmentName())
                .containsExactly("多晶车间", "红糖车间", "单晶车间", "全厂汇总");
        assertThat(result.getSections())
                .flatExtracting(section -> section.getGroups())
                .filteredOn(group -> group.isProductGroup())
                .allSatisfy(group -> assertThat(group.getProductLines()).isEmpty());
    }

    @Test
    void saveSectionIgnoresTheEmptyPlaceholderAndForcesProductUnitToPieces() {
        LocalDate date = LocalDate.of(2026, 8, 26);
        ProductionDailyReport report = new ProductionDailyReport();
        report.setId(10L);
        report.setReportDate(date);
        report.setStatus("DRAFT");
        report.setVersion(0);
        when(reportMapper.selectByReportDate(date)).thenReturn(report);

        ProductionDailyReportSection section = new ProductionDailyReportSection();
        section.setId(20L);
        section.setReportId(10L);
        section.setDepartmentCode("POLYCRYSTAL");
        section.setDepartmentNameSnapshot("多晶车间");
        section.setStatus("DRAFT");
        section.setVersion(0);
        when(sectionMapper.selectByReportAndDepartment(10L, "POLYCRYSTAL")).thenReturn(section);
        when(sectionMapper.updateForSave(eq(20L), eq(0), eq(3), eq("车间主任"), any())).thenReturn(1);
        when(sectionMapper.selectByReportId(10L)).thenReturn(List.of(section));
        when(metricValueMapper.selectBySectionId(anyLong())).thenReturn(List.of());
        when(productLineMapper.selectBySectionId(anyLong())).thenReturn(List.of());

        Product product = new Product();
        product.setId(7);
        product.setProductName("测试半成品");
        product.setStatus("半成品");
        product.setProductType("冰糖");
        product.setPackagingMethod("盒");
        when(productMapper.selectBatchIds(anyCollection())).thenReturn(List.of(product));

        ProductionDailyProductLineDTO emptyPlaceholder = new ProductionDailyProductLineDTO();
        ProductionDailyProductLineDTO productInput = new ProductionDailyProductLineDTO();
        productInput.setCategoryCode("SEMI_PRODUCT");
        productInput.setProductId(7);
        productInput.setUnit("盒");
        ProductionDailyReportSectionSaveDTO dto = new ProductionDailyReportSectionSaveDTO();
        dto.setVersion(0);
        dto.setProductLines(List.of(emptyPlaceholder, productInput));

        service.saveSection(date, "POLYCRYSTAL", dto, 3, "车间主任");

        ArgumentCaptor<ProductionDailyProductLine> captor = ArgumentCaptor.forClass(ProductionDailyProductLine.class);
        verify(productLineMapper).insert(captor.capture());
        assertThat(captor.getValue().getCategoryCode()).isEqualTo("SEMI_PRODUCT");
        assertThat(captor.getValue().getProductNameSnapshot()).isEqualTo("测试半成品");
        assertThat(captor.getValue().getUnit()).isEqualTo("件");
        verify(reportMapper).touchDraft(eq(10L), eq(3), eq("车间主任"), any());
    }

    @Test
    void importReturnsUnmatchedProductsAsAnUnpersistedPreview() {
        LocalDate date = LocalDate.of(2026, 8, 27);
        ProductionDailyReportXlsxImporter.ParsedProductLine product =
                new ProductionDailyReportXlsxImporter.ParsedProductLine(
                        "SEMI_PRODUCT", "Excel中不存在的产品",
                        null, null, null, null, null, null, 1);
        ProductionDailyReportXlsxImporter.ParsedSection section =
                new ProductionDailyReportXlsxImporter.ParsedSection(
                        "POLYCRYSTAL", List.of(), List.of(product));
        when(xlsxImporter.parse(any())).thenReturn(new ProductionDailyReportXlsxImporter.ParsedReport(
                date, date, "测试制表人", List.of(section)));
        when(productMapper.selectProductsByName(null, null, null)).thenReturn(List.of());
        MockMultipartFile file = new MockMultipartFile(
                "file", "生产日报.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1});

        when(reportMapper.selectByReportDate(date)).thenReturn(null);

        ProductionDailyReportImportVO result = service.importReport(file, 3, "车间主任");

        assertThat(result.isSaved()).isFalse();
        assertThat(result.getUnmatchedCount()).isOne();
        assertThat(result.getReport().getPreparedByName()).isEqualTo("测试制表人");
        assertThat(result.getReport().getSections())
                .filteredOn(item -> item.getDepartmentCode().equals("POLYCRYSTAL"))
                .flatExtracting(item -> item.getGroups())
                .filteredOn(group -> group.getGroupCode().equals("SEMI_PRODUCT"))
                .flatExtracting(group -> group.getProductLines())
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.getProductId()).isNull();
                    assertThat(line.getProductName()).isEqualTo("Excel中不存在的产品");
                    assertThat(line.getImportedProductName()).isEqualTo("Excel中不存在的产品");
                    assertThat(line.getImportError()).isEqualTo("未找到匹配产品");
                    assertThat(line.getUnit()).isEqualTo("件");
                });
        verify(reportMapper).selectByReportDate(date);
        verifyNoInteractions(sectionMapper, metricValueMapper, productLineMapper);
    }
}
