package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayAbnormalitiesVO;
import com.Laibin.SugarInventory.mapper.AssayAbnormalitiesMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.model.AssayAbnormalityGroupRow;
import com.Laibin.SugarInventory.mapper.model.AssayAbnormalitySummaryRow;
import com.Laibin.SugarInventory.service.impl.AssayAbnormalitiesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssayAbnormalitiesServiceImplTest {
    private AssayAbnormalitiesMapper assayAbnormalitiesMapper;
    private ProductMapper productMapper;
    private AssayAbnormalitiesService service;

    @BeforeEach
    void setUp() {
        assayAbnormalitiesMapper = mock(AssayAbnormalitiesMapper.class);
        productMapper = mock(ProductMapper.class);
        service = new AssayAbnormalitiesServiceImpl(assayAbnormalitiesMapper, productMapper);
    }

    @Test
    void queriesAbnormalitiesWithSafeGroupedSummary() {
        when(productMapper.selectById(84)).thenReturn(product());
        AssayAbnormalitySummaryRow summary = new AssayAbnormalitySummaryRow();
        summary.setTotal(2L);
        summary.setFailedCount(1L);
        summary.setNoStandardCount(1L);
        summary.setLatestSampleDate(LocalDate.of(2026, 7, 8));
        when(assayAbnormalitiesMapper.selectSummary(any())).thenReturn(summary);
        when(assayAbnormalitiesMapper.selectGroups(any())).thenReturn(List.of(group()));

        AssayAbnormalitiesVO result = service.queryAbnormalities(query());

        assertThat(result.getSummaryText()).contains("2 条化验质量异常");
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getNoStandardCount()).isEqualTo(1);
        assertThat(result.getRiskLabels()).contains("存在不合格化验", "存在无标准化验");
        assertThat(result.getGroups()).hasSize(1);
        assertThat(result.getGroups().get(0).getGroupLabel()).contains("黄冰糖");
        assertThat(result.toString()).doesNotContain("productId", "assayId");

        ArgumentCaptor<AssayAbnormalitiesQueryDTO> captor = ArgumentCaptor.forClass(AssayAbnormalitiesQueryDTO.class);
        verify(assayAbnormalitiesMapper).selectSummary(captor.capture());
        assertThat(captor.getValue().getResolvedJudgeResults()).containsExactly("FAIL", "NO_STANDARD");
        assertThat(captor.getValue().getResolvedFrom()).isNotNull();
        assertThat(captor.getValue().getResolvedTo()).isNotNull();
    }

    @Test
    void rejectsNoAssayAsAbnormalType() {
        AssayAbnormalitiesQueryDTO invalid = query();
        invalid.setAbnormalTypes(List.of("NO_ASSAY"));

        assertThatThrownBy(() -> service.queryAbnormalities(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("异常类型");
    }

    private AssayAbnormalitiesQueryDTO query() {
        AssayRecordsQueryDTO.ProductScope scope = new AssayRecordsQueryDTO.ProductScope();
        scope.setType("SINGLE_PRODUCT");
        scope.setProductId(84);
        AssayRecordsQueryDTO.DateRange range = new AssayRecordsQueryDTO.DateRange();
        range.setType("LAST_DAYS");
        range.setDays(7);
        AssayAbnormalitiesQueryDTO query = new AssayAbnormalitiesQueryDTO();
        query.setProductScope(scope);
        query.setDateRange(range);
        query.setAbnormalTypes(List.of("FAILED", "NO_STANDARD"));
        query.setGroupBy("product");
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

    private AssayAbnormalityGroupRow group() {
        AssayAbnormalityGroupRow row = new AssayAbnormalityGroupRow();
        row.setProductName("黄冰糖");
        row.setPackagingMethod("袋");
        row.setWeightPerPiece(new BigDecimal("25"));
        row.setPiecesPerPallet(40);
        row.setTotal(2L);
        row.setFailedCount(1L);
        row.setNoStandardCount(1L);
        row.setLatestSampleDate(LocalDate.of(2026, 7, 8));
        return row;
    }
}
