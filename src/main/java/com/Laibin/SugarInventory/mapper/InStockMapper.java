package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.po.InStock;
import com.Laibin.SugarInventory.domain.po.InventoryLocation;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface InStockMapper extends BaseMapper<InStock> {

    @Insert("INSERT INTO in_stock (product_id, warehouse_id, quantity, weight_per_piece, total_weight, " +
            "screen_mesh_id, semi_product_record_id, assay_id, entry_date, created_by, created_at) " +
            "VALUES (#{productId}, #{warehouseId}, #{quantity}, #{weightPerPiece}, #{totalWeight}, " +
            "#{screenMeshId}, #{semiProductRecordId}, #{assayId}, #{entryDate}, #{createdBy}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(InStock inStock);

    @Select("<script>" +
            "SELECT s.*, p.product_name, w.warehouse_id, u.name as operator_name, " +
            "sm.screen_mesh_name, spr.id as semi_product_record_id, " +
            "FROM in_stock s " +
            "LEFT JOIN product p ON s.product_id = p.id " +
            "LEFT JOIN warehouse w ON s.warehouse_id = w.id " +
            "LEFT JOIN user u ON s.created_by = u.id " +
            "LEFT JOIN screen_mesh sm ON s.screen_mesh_id = sm.id " +
            "LEFT JOIN semi_product_record spr ON s.semi_product_record_id = spr.id " +
            "<where>" +
            "   <if test='query.productName != null'> AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') </if>" +
            "   <if test='query.warehouseId != null'> AND s.warehouse_id = #{query.warehouseId} </if>" +
            "   <if test='query.startDate != null'> AND s.entry_date &gt;= #{query.startDate} </if>" +
            "   <if test='query.endDate != null'> AND s.entry_date &lt;= #{query.endDate} </if>" +
            "   <if test='query.operatorName != null'> AND u.name LIKE CONCAT('%', #{query.operatorName}, '%') </if>" +
            "   <if test='isStaff'> AND s.created_by = #{userId} </if>" +
            "</where>" +
            "ORDER BY s.entry_date DESC " +
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
            "<where>" +
            "   <if test='query.productName != null'> AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') </if>" +
            "   <if test='query.warehouseId != null'> AND s.warehouse_id = #{query.warehouseId} </if>" +
            "   <if test='query.startDate != null'> AND s.entry_date &gt;= #{query.startDate} </if>" +
            "   <if test='query.endDate != null'> AND s.entry_date &lt;= #{query.endDate} </if>" +
            "   <if test='query.operatorName != null'> AND u.name LIKE CONCAT('%', #{query.operatorName}, '%') </if>" +
            "   <if test='isStaff'> AND s.created_by = #{userId} </if>" +
            "</where>" +
            "</script>")
    Long countInStockRecords(
            @Param("query") InStockQueryDTO query,
            @Param("userId") Integer userId,
            @Param("isStaff") Boolean isStaff
    );
}
