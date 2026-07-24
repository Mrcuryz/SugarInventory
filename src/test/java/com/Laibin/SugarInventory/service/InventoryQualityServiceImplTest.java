package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryQualityQueryDTO;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.vo.InventoryQualityVO;
import com.Laibin.SugarInventory.mapper.InventoryQualityMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.model.InventoryQualityAggregateRow;
import com.Laibin.SugarInventory.mapper.model.InventoryQualityRow;
import com.Laibin.SugarInventory.service.impl.InventoryQualityServiceImpl;
import com.Laibin.SugarInventory.service.support.AssayReportRefCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryQualityServiceImplTest {
    private InventoryQualityMapper qualityMapper;
    private QualityStandardMapper standardMapper;
    private InventoryQualityService service;

    @BeforeEach
    void setUp() {
        qualityMapper = mock(InventoryQualityMapper.class);
        standardMapper = mock(QualityStandardMapper.class);
        service = new InventoryQualityServiceImpl(qualityMapper, mock(ProductMapper.class), mock(WarehouseMapper.class),
                standardMapper, new AssayReportRefCodec(), new ObjectMapper());
    }

    @Test
    void presentsCurrentFailedInventoryWithFailedMetricsAndSafeReportRef() {
        InventoryQualityAggregateRow aggregate = new InventoryQualityAggregateRow();
        aggregate.setTotalGroups(1L);
        aggregate.setTotalEquivalentPieces(40L);
        aggregate.setTotalWeight(new BigDecimal("1000"));
        when(qualityMapper.selectAggregate(any())).thenReturn(aggregate);
        InventoryQualityRow row = row();
        row.setJudgeResult("FAIL");
        row.setFailedMetricCount(2);
        row.setFailedMetricsJson("[{\"metricName\":\"色值\"},{\"metricName\":\"pH\"}]");
        when(qualityMapper.selectRecords(any())).thenReturn(List.of(row));

        InventoryQualityQueryDTO query = base("JUDGE_STATUS");
        query.setJudgeStatus("FAIL");
        InventoryQualityVO result = service.query(query);

        assertThat(result.getQueryLabel()).contains("当前不合格库存");
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().getFirst().getFailedMetricText()).isEqualTo("色值、pH");
        assertThat(result.getRecords().getFirst().getReportRef()).startsWith("assay_report_");
        assertThat(result.getNotes()).anyMatch(note -> note.contains("无化验和无标准") && note.contains("不计入不合格"));
    }

    @Test
    void resolvesControlledStandardCodeAndReportsMatchedStandard() {
        QualityStandard standard = new QualityStandard();
        standard.setId(7);
        standard.setStandardCode("YBT-V1");
        standard.setStandardName("黄冰糖一级");
        standard.setProductType("黄冰糖");
        standard.setVersion(1);
        when(standardMapper.selectByCodeAndVersion("YBT-V1", null)).thenReturn(standard);
        when(qualityMapper.selectAggregate(any())).thenReturn(new InventoryQualityAggregateRow());
        when(qualityMapper.selectRecords(any())).thenReturn(List.of());
        InventoryQualityQueryDTO query = base("STANDARD");
        query.setStandardCode("YBT-V1");

        InventoryQualityVO result = service.query(query);

        assertThat(query.getResolvedStandardId()).isEqualTo(7);
        assertThat(result.getQueryLabel()).contains("黄冰糖一级 v1");
    }

    @Test
    void rejectsOpenEndedOrInvalidMetricConditions() {
        InventoryQualityQueryDTO query = base("METRIC");
        InventoryQualityQueryDTO.MetricCondition condition = new InventoryQualityQueryDTO.MetricCondition();
        condition.setMetricCode("sucrose");
        condition.setOperator("BETWEEN");
        condition.setMinValue(new BigDecimal("99.9"));
        condition.setMaxValue(new BigDecimal("99.7"));
        query.setMetricCondition(condition);

        assertThatThrownBy(() -> service.query(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最小值和最大值");
    }

    private InventoryQualityQueryDTO base(String mode) {
        InventoryDistributionQueryDTO.ProductScope productScope = new InventoryDistributionQueryDTO.ProductScope();
        productScope.setType("ALL");
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope = new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("ALL");
        InventoryQualityQueryDTO query = new InventoryQualityQueryDTO();
        query.setProductScope(productScope);
        query.setWarehouseScope(warehouseScope);
        query.setMode(mode);
        return query;
    }

    private InventoryQualityRow row() {
        InventoryQualityRow row = new InventoryQualityRow();
        row.setAssayId(101);
        row.setProductId(84);
        row.setProductName("黄冰糖");
        row.setPackagingMethod("袋");
        row.setWeightPerPiece(new BigDecimal("25"));
        row.setPiecesPerPallet(40);
        row.setProductionDate(LocalDate.of(2026, 7, 17));
        row.setWarehouseName("1");
        row.setTotalEquivalentPieces(40L);
        row.setTotalWeight(new BigDecimal("1000"));
        row.setPalletCount(1L);
        return row;
    }
}
