package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.InventoryDistributionQueryDTO;
import com.Laibin.SugarInventory.mapper.model.InventoryDistributionAggregateRow;
import com.Laibin.SugarInventory.mapper.model.InventoryDistributionGroupRow;
import com.Laibin.SugarInventory.mapper.sql.InventoryDistributionSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface InventoryDistributionMapper {
    @SelectProvider(type = InventoryDistributionSqlProvider.class, method = "selectAggregate")
    InventoryDistributionAggregateRow selectAggregate(@Param("query") InventoryDistributionQueryDTO query);

    @SelectProvider(type = InventoryDistributionSqlProvider.class, method = "selectGroups")
    List<InventoryDistributionGroupRow> selectGroups(@Param("query") InventoryDistributionQueryDTO query);
}
