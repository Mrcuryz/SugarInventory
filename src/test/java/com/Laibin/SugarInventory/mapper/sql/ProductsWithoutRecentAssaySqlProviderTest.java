package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductsWithoutRecentAssaySqlProviderTest {

    @Test
    void groupQueryKeepsWhitespaceBetweenSelectListAndFromClause() {
        ProductsWithoutRecentAssayQueryDTO query = new ProductsWithoutRecentAssayQueryDTO();
        AssayRecordsQueryDTO.ProductScope productScope = new AssayRecordsQueryDTO.ProductScope();
        productScope.setType("ALL");
        query.setProductScope(productScope);
        InventoryDistributionQueryDTO.WarehouseScope warehouseScope = new InventoryDistributionQueryDTO.WarehouseScope();
        warehouseScope.setType("ALL");
        query.setWarehouseScope(warehouseScope);
        query.setGroupBy("product");
        query.setLimit(50);

        String sql = new ProductsWithoutRecentAssaySqlProvider().selectGroups(Map.of("query", query));

        assertThat(sql).contains("AS warehouseNames FROM inventory i");
        assertThat(sql).contains("CASE WHEN COALESCE(i.pieces, 0) > 0");
        assertThat(sql).contains("THEN COALESCE(i.pieces, 0)");
        assertThat(sql).doesNotContain("p.pieces_per_pallet + COALESCE(i.pieces, 0)");
        assertThat(sql).doesNotContain("warehouseNamesFROM");
    }
}
