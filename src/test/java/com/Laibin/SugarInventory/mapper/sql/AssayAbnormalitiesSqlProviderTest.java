package com.Laibin.SugarInventory.mapper.sql;

import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayRecordsQueryDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssayAbnormalitiesSqlProviderTest {

    @Test
    void escapesUpperDateBoundForMyBatisXmlScript() {
        AssayRecordsQueryDTO.ProductScope scope = new AssayRecordsQueryDTO.ProductScope();
        scope.setType("ALL");

        AssayAbnormalitiesQueryDTO query = new AssayAbnormalitiesQueryDTO();
        query.setProductScope(scope);
        query.setResolvedFrom(LocalDate.of(2026, 7, 1));
        query.setResolvedTo(LocalDate.of(2026, 7, 7));
        query.setResolvedJudgeResults(List.of("NO_STANDARD"));

        String sql = new AssayAbnormalitiesSqlProvider().selectSummary(Map.of("query", query));

        assertThat(sql)
                .contains("a.sample_date &lt;= #{query.resolvedTo}")
                .doesNotContain("a.sample_date <= #{query.resolvedTo}");
    }
}
