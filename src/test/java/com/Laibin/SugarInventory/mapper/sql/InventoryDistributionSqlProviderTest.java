package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDistributionSqlProviderTest {

    @Test
    void joinsLatestAssayByProductAndProductionDateInsteadOfLegacyForeignKey() {
        InventoryDistributionQueryDTO query = query("FAIL");

        String sql = new InventoryDistributionSqlProvider().selectGroups(Map.of("query", query));

        assertThat(sql)
                .contains("ROW_NUMBER() OVER", "PARTITION BY a.product_id, a.sample_date")
                .contains("a.product_id = i.product_id", "a.sample_date = COALESCE(pc.production_date, i.entry_date)")
                .contains("a.judge_result = #{query.statusFilter.assayStatus}")
                .doesNotContain("a.id = i.assay_id", "i.assay_id IS NOT NULL", "i.assay_id IS NULL");
    }

    @Test
    void missingAssayUsesAbsenceOfLatestBatchAssay() {
        InventoryDistributionQueryDTO query = query("MISSING_ASSAY");

        String sql = new InventoryDistributionSqlProvider().selectAggregate(Map.of("query", query));

        assertThat(sql).contains("a.id IS NULL");
        assertThat(sql).doesNotContain("i.assay_id IS NULL");
    }

    private InventoryDistributionQueryDTO query(String assayStatus) {
        InventoryDistributionQueryDTO.ProductScope productScope = new InventoryDistributionQueryDTO.ProductScope();
        productScope.setType("ALL");
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope = new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("ALL");
        InventoryDistributionQueryDTO.StatusFilter filter = new InventoryDistributionQueryDTO.StatusFilter();
        filter.setAssayStatus(assayStatus);
        InventoryDistributionQueryDTO query = new InventoryDistributionQueryDTO();
        query.setProductScope(productScope);
        query.setWarehouseScope(warehouseScope);
        query.setStatusFilter(filter);
        query.setGroupBy("product");
        return query;
    }
}
