package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.AssayAbnormalitiesQueryDTO;
import com.Laibin.SugarInventory.mapper.model.AssayAbnormalityGroupRow;
import com.Laibin.SugarInventory.mapper.model.AssayAbnormalitySummaryRow;
import com.Laibin.SugarInventory.mapper.sql.AssayAbnormalitiesSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface AssayAbnormalitiesMapper {
    @SelectProvider(type = AssayAbnormalitiesSqlProvider.class, method = "selectSummary")
    AssayAbnormalitySummaryRow selectSummary(@Param("query") AssayAbnormalitiesQueryDTO query);

    @SelectProvider(type = AssayAbnormalitiesSqlProvider.class, method = "selectGroups")
    List<AssayAbnormalityGroupRow> selectGroups(@Param("query") AssayAbnormalitiesQueryDTO query);
}
