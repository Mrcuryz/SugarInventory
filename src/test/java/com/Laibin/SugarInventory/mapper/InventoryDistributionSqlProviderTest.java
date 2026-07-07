package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.mapper.sql.InventoryDistributionSqlProvider;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryDistributionSqlProviderTest {
    private final InventoryDistributionSqlProvider provider = new InventoryDistributionSqlProvider();

    @Test
    void buildsProductWarehouseGroupingWithBoundScopeAndFilters() {
        InventoryDistributionQueryDTO query = query();
        query.getProductScope().setType("PRODUCT_TYPE_GROUP");
        query.getProductScope().setProductType("黄冰糖' OR 1=1 --");
        query.getWarehouseScope().setType("SINGLE_WAREHOUSE");
        query.getWarehouseScope().setWarehouseId(2);
        query.setGroupBy("warehouse_product");
        query.getStatusFilter().setProductStatuses(List.of("成品"));
        query.getStatusFilter().setWarehouseStatuses(List.of("正常"));
        query.getStatusFilter().setPalletStatuses(List.of("INSTOCK"));
        query.getStatusFilter().setAssayStatus("PASS");
        query.getStatusFilter().setEntryDateFrom(LocalDate.of(2026, 7, 1));
        query.getStatusFilter().setEntryDateTo(LocalDate.of(2026, 7, 7));

        String sql = provider.selectGroups(Map.of("query", query));

        assertThat(sql).contains("GROUP BY i.warehouse_id", "i.product_id", "LIMIT #{query.limit}");
        assertThat(sql).contains("#{query.productScope.productType}", "#{query.warehouseScope.warehouseId}");
        assertThat(sql).contains("#{query.statusFilter.productStatuses[0]}");
        assertThat(sql).contains("a.judge_result = #{query.statusFilter.assayStatus}");
        assertThat(sql).doesNotContain("黄冰糖' OR 1=1 --");
    }

    @Test
    void allScopeDoesNotAddIdsAndUsesControlledProductGrouping() {
        InventoryDistributionQueryDTO query = query();
        query.getProductScope().setType("ALL");
        query.setGroupBy("product");

        String sql = provider.selectGroups(Map.of("query", query));

        assertThat(sql).contains("GROUP BY i.product_id");
        assertThat(sql).doesNotContain("productScope.productId", "warehouseScope.warehouseId");
        assertThat(sql).doesNotContain("${");
    }

    @Test
    void exactNameGroupMatchesBracketedSpecificationsWithBoundParameter() {
        InventoryDistributionQueryDTO query = query();
        query.getProductScope().setType("EXACT_PRODUCT_NAME_GROUP");
        query.getProductScope().setProductName("黄冰糖");

        String sql = provider.selectAggregate(Map.of("query", query));

        assertThat(sql).contains("p.product_name = #{query.productScope.productName}");
        assertThat(sql).contains("LIKE CONCAT(#{query.productScope.productName}, '（%')");
        assertThat(sql).doesNotContain("p.product_name = '黄冰糖'");
    }

    private InventoryDistributionQueryDTO query() {
        InventoryDistributionQueryDTO.ProductScope productScope = new InventoryDistributionQueryDTO.ProductScope();
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope = new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("ALL");
        InventoryDistributionQueryDTO query = new InventoryDistributionQueryDTO();
        query.setProductScope(productScope);
        query.setWarehouseScope(warehouseScope);
        query.setGroupBy("warehouse");
        query.setLimit(20);
        return query;
    }
}
