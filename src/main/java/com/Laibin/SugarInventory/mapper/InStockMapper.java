package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.SemiRecordDTO;
import com.Laibin.SugarInventory.domain.po.InStock;
import com.Laibin.SugarInventory.domain.po.InventoryLocation;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface InStockMapper extends BaseMapper<InStock> {

    @Insert("INSERT INTO in_stock (product_id, warehouse_id, quantity, total_weight, " +
            "screen_mesh_id, semi_product_records, assay_id, entry_date, created_by, created_at, unit) " +
            "VALUES (#{productId}, #{warehouseId}, #{quantity}, #{totalWeight}, " +
            "#{screenMeshId}, #{semiProductRecords}, #{assayId}, #{entryDate}, #{createdBy}, #{createdAt}, #{unit})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InStock inStock);

    @Select("<script>" +
            "SELECT s.*, p.product_name, w.warehouse_name, u.name as operator, a.*, " +
            "sm.mesh_name " +
            "FROM in_stock s " +
            "LEFT JOIN product p ON s.product_id = p.id " +
            "LEFT JOIN warehouse w ON s.warehouse_id = w.id " +
            "LEFT JOIN user u ON s.created_by = u.id " +
            "LEFT JOIN screen_mesh sm ON s.screen_mesh_id = sm.id " +
            "LEFT JOIN ( " +
            "    SELECT * " +
            "    FROM ( " +
            "        SELECT a.*, " +
            "               ROW_NUMBER() OVER (PARTITION BY product_id, sample_date ORDER BY version DESC) AS rn " +
            "        FROM assay a " +
            "    ) ranked " +
            "    WHERE rn = 1 " +
            ") a ON s.product_id = a.product_id AND s.entry_date = a.sample_date " +
            "where 1=1 " +
            "<if test='query.productName != null'> " +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if>" +
            "<if test='query.warehouseName != null'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{query.warehouseName}, '%') " +
            "</if> " +
            "<if test='query.startDate != null'> " +
            "   AND s.entry_date &gt;= #{query.startDate} " +
            "</if>" +
            "<if test='query.endDate != null'> " +
            "   AND s.entry_date &lt;= #{query.endDate} " +
            "</if>" +
            "<if test='query.operatorName != null'> " +
            "   AND u.name LIKE CONCAT('%', #{query.operatorName}, '%') " +
            "</if>" +
            "<if test='isStaff'> " +
            "   AND s.created_by = #{userId} " +
            "</if>" +
            "ORDER BY s.created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<InStockVO> selectInStockList(
            @Param("query") InStockQueryDTO query,
            @Param("userId") Integer userId,
            @Param("isStaff") Boolean isStaff,
            @Param("offset") int offset,
            @Param("size") int size
    );

    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM in_stock s " +
            "LEFT JOIN product p ON s.product_id = p.id " +
            "LEFT JOIN user u ON s.created_by = u.id " +
            "LEFT JOIN warehouse w ON s.warehouse_id = w.id " +
            "LEFT JOIN screen_mesh sm ON s.screen_mesh_id = sm.id " +
            "LEFT JOIN assay a ON s.assay_id = a.id " +
            "where 1=1 " +
            "<if test='query.productName != null'> " +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if>" +
            "<if test='query.warehouseName != null'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{query.warehouseName}, '%') " +
            "</if> " +
            "<if test='query.startDate != null'> " +
            "   AND s.entry_date &gt;= #{query.startDate} " +
            "</if>" +
            "<if test='query.endDate != null'> " +
            "   AND s.entry_date &lt;= #{query.endDate} " +
            "</if>" +
            "<if test='query.operatorName != null'> " +
            "   AND u.name LIKE CONCAT('%', #{query.operatorName}, '%') " +
            "</if>" +
            "<if test='isStaff'> " +
            "   AND s.created_by = #{userId} " +
            "</if>" +
            "</script>")
    Long countInStockRecords(
            @Param("query") InStockQueryDTO query,
            @Param("userId") Integer userId,
            @Param("isStaff") Boolean isStaff
    );

    @Insert("<script>" +
            "INSERT INTO in_stock_item (in_stock_id, product_name, quantity, warehouse_id, semi_product_id, production_date, use_assay, unit) " +
            "VALUES " +
            "<foreach item='item' index='index' collection='semiRecords' separator=','>" +
            "(#{inStockId}, #{item.productName}, #{item.quantity}, #{item.warehouseId}, #{item.semiProductId}, #{item.productionDate}, #{item.useAssay}, #{item.unit})" +
            "</foreach>" +
            "</script>")
    void saveInStockItem(@Param("semiRecords") List<SemiRecordDTO> semiRecords, @Param("inStockId") Integer inStockId);
}
