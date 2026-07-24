package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryQualityQueryDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryQualitySqlProviderTest {
    private final InventoryQualitySqlProvider provider = new InventoryQualitySqlProvider();

    @Test
    void failedInventoryUsesLatestBatchAssayAndNeverLegacyAssayForeignKey() {
        InventoryQualityQueryDTO query = base("JUDGE_STATUS");
        query.setJudgeStatus("FAIL");

        String sql = provider.selectRecords(Map.of("query", query));

        assertThat(sql)
                .contains("PARTITION BY a.product_id, a.sample_date")
                .contains("a.product_id = i.product_id", "a.sample_date = COALESCE(pc.production_date, i.entry_date)")
                .contains("a.judge_result = #{query.judgeStatus}")
                .doesNotContain("a.id = i.assay_id");
    }

    @Test
    void standardMatchEvaluatesEveryConfiguredMetricAgainstControlledStandard() {
        InventoryQualityQueryDTO query = base("STANDARD");
        query.setStandardCode("YBT-V1");
        query.setResolvedStandardId(7);
        query.setResolvedStandardProductType("黄冰糖");

        String sql = provider.selectAggregate(Map.of("query", query));

        assertThat(sql)
                .contains("quality_standard_item")
                .contains("qsi.quality_standard_id = #{query.resolvedStandardId}")
                .contains("WHEN 'dry_weight_loss' THEN a.dry_weight")
                .contains("WHEN 'ph' THEN a.ph_value")
                .contains("NOT EXISTS");
    }

    @Test
    void metricColumnAndOperatorComeFromClosedEnums() {
        InventoryQualityQueryDTO query = base("METRIC");
        InventoryQualityQueryDTO.MetricCondition condition = new InventoryQualityQueryDTO.MetricCondition();
        condition.setMetricCode("sucrose");
        condition.setOperator("GTE");
        condition.setValue(new BigDecimal("99.7"));
        query.setMetricCondition(condition);

        String sql = provider.selectRecords(Map.of("query", query));

        assertThat(sql).contains("a.sucrose >= #{query.metricCondition.value}");
        assertThat(sql).doesNotContain("99.7");
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
}
