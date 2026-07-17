package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.AssayStandardCoverageQueryDTO;
import com.Laibin.SugarInventory.mapper.model.AssayStandardCoverageGroupRow;
import com.Laibin.SugarInventory.mapper.sql.AssayStandardCoverageSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface AssayStandardCoverageMapper {
    @SelectProvider(type = AssayStandardCoverageSqlProvider.class, method = "selectProductWithoutStandardGroups")
    List<AssayStandardCoverageGroupRow> selectProductWithoutStandardGroups(
            @Param("query") AssayStandardCoverageQueryDTO query);
}
