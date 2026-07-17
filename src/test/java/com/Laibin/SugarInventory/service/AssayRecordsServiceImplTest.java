package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayRecordsVO;
import com.Laibin.SugarInventory.mapper.AssayRecordsMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.model.AssayRecordRow;
import com.Laibin.SugarInventory.mapper.model.AssayRecordsSummaryRow;
import com.Laibin.SugarInventory.service.impl.AssayRecordsServiceImpl;
import com.Laibin.SugarInventory.service.support.AssayReportRefCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssayRecordsServiceImplTest {
    private AssayRecordsMapper assayRecordsMapper;
    private ProductMapper productMapper;
    private AssayRecordsService service;

    @BeforeEach
    void setUp() {
        assayRecordsMapper = mock(AssayRecordsMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new AssayRecordsServiceImpl(assayRecordsMapper, productMapper, new ObjectMapper(), new AssayReportRefCodec());
    }

    @Test
    void queriesSingleProductRecentFailedRecordsWithSafeOutput() {
        when(productMapper.selectById(84)).thenReturn(product());
        AssayRecordsSummaryRow summary = new AssayRecordsSummaryRow();
        summary.setTotal(1L);
        summary.setFailedCount(1L);
        summary.setLatestSampleDate(LocalDate.of(2026, 7, 8));
        when(assayRecordsMapper.selectSummary(any())).thenReturn(summary);
        when(assayRecordsMapper.selectRecords(any(), eq(0), eq(20))).thenReturn(List.of(record()));

        AssayRecordsVO result = service.queryRecords(query());

        assertThat(result.getScopeLabel()).contains("黄冰糖（袋）");
        assertThat(result.getDateRangeLabel()).isEqualTo("最近30天");
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSummaryText()).contains("不合格化验记录");
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getRecordRef()).startsWith("assay_report_");
        assertThat(result.getRecords().get(0).getJudgeLabel()).isEqualTo("不合格");
        assertThat(result.getRecords().get(0).getFailedMetricText()).isEqualTo("色值");
        assertThat(result.toString()).doesNotContain("productId", "assayId");

        ArgumentCaptor<AssayRecordsQueryDTO> captor = ArgumentCaptor.forClass(AssayRecordsQueryDTO.class);
        verify(assayRecordsMapper).selectSummary(captor.capture());
        assertThat(captor.getValue().getResolvedJudgeResult()).isEqualTo("FAIL");
        assertThat(captor.getValue().getResolvedFrom()).isNotNull();
        assertThat(captor.getValue().getResolvedTo()).isNotNull();
    }

    @Test
    void rejectsInvalidScopeDateAndSize() {
        AssayRecordsQueryDTO missingProduct = new AssayRecordsQueryDTO();
        assertThatThrownBy(() -> service.queryRecords(missingProduct))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("产品范围");

        AssayRecordsQueryDTO invalidDate = query();
        invalidDate.getDateRange().setType("RANGE");
        invalidDate.getDateRange().setFrom(LocalDate.of(2026, 7, 9));
        invalidDate.getDateRange().setTo(LocalDate.of(2026, 7, 1));
        assertThatThrownBy(() -> service.queryRecords(invalidDate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("开始日期");

        AssayRecordsQueryDTO invalidSize = query();
        invalidSize.setSize(101);
        assertThatThrownBy(() -> service.queryRecords(invalidSize))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size");
    }

    private AssayRecordsQueryDTO query() {
        AssayRecordsQueryDTO.ProductScope scope = new AssayRecordsQueryDTO.ProductScope();
        scope.setType("SINGLE_PRODUCT");
        scope.setProductId(84);
        AssayRecordsQueryDTO.DateRange range = new AssayRecordsQueryDTO.DateRange();
        range.setType("LAST_DAYS");
        range.setDays(30);
        AssayRecordsQueryDTO query = new AssayRecordsQueryDTO();
        query.setProductScope(scope);
        query.setDateRange(range);
        query.setJudgeStatus("FAILED");
        query.setPage(1);
        query.setSize(20);
        return query;
    }

    private Product product() {
        Product product = new Product();
        product.setId(84);
        product.setProductName("黄冰糖");
        product.setPackagingMethod("袋");
        product.setWeightPerPiece(new BigDecimal("25.0"));
        product.setPiecesPerPallet(40);
        return product;
    }

    private AssayRecordRow record() {
        AssayRecordRow row = new AssayRecordRow();
        row.setId(1001);
        row.setProductId(84);
        row.setProductName("黄冰糖");
        row.setPackagingMethod("袋");
        row.setWeightPerPiece(new BigDecimal("25"));
        row.setPiecesPerPallet(40);
        row.setSampleDate(LocalDate.of(2026, 7, 8));
        row.setCreatedAt(LocalDateTime.of(2026, 7, 8, 10, 0));
        row.setJudgeResult("FAIL");
        row.setFailedMetricCount(1);
        row.setFailedMetricsJson("[{\"metricName\":\"色值\"}]");
        row.setAppliedStandardName("黄冰糖标准");
        row.setAppliedStandardVersion(3);
        row.setTesterName("张三");
        return row;
    }
}
