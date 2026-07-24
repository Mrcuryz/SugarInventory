package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.InventoryQualityQueryDTO;
import com.Laibin.SugarInventory.mapper.model.InventoryQualityAggregateRow;
import com.Laibin.SugarInventory.mapper.model.InventoryQualityRow;
import com.Laibin.SugarInventory.mapper.sql.InventoryQualitySqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface InventoryQualityMapper {
    @SelectProvider(type = InventoryQualitySqlProvider.class, method = "selectAggregate")
    InventoryQualityAggregateRow selectAggregate(@Param("query") InventoryQualityQueryDTO query);

    @SelectProvider(type = InventoryQualitySqlProvider.class, method = "selectRecords")
    List<InventoryQualityRow> selectRecords(@Param("query") InventoryQualityQueryDTO query);
}
