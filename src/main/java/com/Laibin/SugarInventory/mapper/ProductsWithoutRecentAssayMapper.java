package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.ProductsWithoutRecentAssayQueryDTO;
import com.Laibin.SugarInventory.mapper.model.ProductsWithoutRecentAssayAggregateRow;
import com.Laibin.SugarInventory.mapper.model.ProductsWithoutRecentAssayGroupRow;
import com.Laibin.SugarInventory.mapper.sql.ProductsWithoutRecentAssaySqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface ProductsWithoutRecentAssayMapper {
    @SelectProvider(type = ProductsWithoutRecentAssaySqlProvider.class, method = "selectAggregate")
    ProductsWithoutRecentAssayAggregateRow selectAggregate(@Param("query") ProductsWithoutRecentAssayQueryDTO query);

    @SelectProvider(type = ProductsWithoutRecentAssaySqlProvider.class, method = "selectGroups")
    List<ProductsWithoutRecentAssayGroupRow> selectGroups(@Param("query") ProductsWithoutRecentAssayQueryDTO query);
}
