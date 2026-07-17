package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssayStandardCoverageSqlProviderTest {

    @Test
    void currentInventoryMetricsUsePartialPalletPiecesWithoutDoubleCounting() {
        AssayStandardCoverageQueryDTO query = new AssayStandardCoverageQueryDTO();
        AssayRecordsQueryDTO.ProductScope productScope = new AssayRecordsQueryDTO.ProductScope();
        productScope.setType("ALL");
        query.setProductScope(productScope);
        query.setResolvedAt(LocalDateTime.of(2026, 7, 15, 10, 0));

        String sql = new AssayStandardCoverageSqlProvider()
                .selectProductWithoutStandardGroups(Map.of("query", query));

        assertThat(sql).contains("CASE WHEN COALESCE(i.pieces, 0) > 0");
        assertThat(sql).contains("THEN COALESCE(i.pieces, 0)");
        assertThat(sql).contains("ELSE COALESCE(i.quantity, 0) * p.pieces_per_pallet END");
        assertThat(sql).doesNotContain("p.pieces_per_pallet + COALESCE(i.pieces, 0)");
    }
}
