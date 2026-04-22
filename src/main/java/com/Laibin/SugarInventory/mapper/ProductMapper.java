package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.ProductInfoVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    @Select("SELECT COUNT(*) FROM product WHERE product_name = #{name}")
    boolean existsByName(@Param("name") String name);

    @Select("<script>" +
            "SELECT p.*, qs.standard_name AS default_standard_name " +
            "FROM product p " +
            "LEFT JOIN product_quality_standard_relation r ON r.product_id = p.id AND r.is_default = 1 AND r.enabled = 1 " +
            "LEFT JOIN quality_standards qs ON qs.id = r.quality_standard_id " +
            "WHERE 1=1 " +
            "<if test='name != null and name != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{name}, '%') " +
            "</if>" +
            "<if test='type != null'>" +
            "   AND p.product_type = #{type} " +
            "</if>" +
            "<if test='status != null'>" +
            "   AND p.status = #{status} " +
            "</if>" +
            "ORDER BY p.id ASC " +
            "</script>")
    List<Product> selectProductsByName(
            @Param("name") String name,
            @Param("type") String type,
            @Param("status") String status
    );

    @Select("SELECT DISTINCT id AS productId, product_name AS productName, " +
            "packaging_method AS packagingMethod, product_type AS productType, " +
            "weight_per_piece AS weightPerPiece " +
            "FROM product WHERE status = '半成品'")
    List<ProductInfoVO> selectSemiProductNames();

    @Select("<script>" +
            "SELECT id AS productId, product_name AS productName, " +
            "packaging_method AS packagingMethod, product_type AS productType, " +
            "weight_per_piece AS weightPerPiece " +
            "FROM product " +
            "WHERE status = '半成品' " +
            "<if test='name != null and name != \"\"'>" +
            "   AND product_name LIKE CONCAT('%', #{name}, '%') " +
            "</if>" +
            "<if test='type != null'>" +
            "   AND product_type = #{type} " +
            "</if>" +
            "</script>")
    List<ProductInfoVO> selectSemiProductsByCondition(
            @Param("name") String name,
            @Param("type") String type
    );

    @Select("SELECT DISTINCT id AS productId, product_name AS productName, " +
            "packaging_method AS packagingMethod, product_type AS productType, " +
            "weight_per_piece AS weightPerPiece " +
            "FROM product WHERE status = '成品'")
    List<ProductInfoVO> selectFinishedProductNames();

    @Select("<script>" +
            "SELECT id AS productId, product_name AS productName, " +
            "packaging_method AS packagingMethod, product_type AS productType, " +
            "weight_per_piece AS weightPerPiece " +
            "FROM product " +
            "WHERE status = '成品' " +
            "<if test='name != null and name != \"\"'>" +
            "   AND product_name LIKE CONCAT('%', #{name}, '%') " +
            "</if>" +
            "<if test='type != null'>" +
            "   AND product_type = #{type} " +
            "</if>" +
            "</script>")
    List<ProductInfoVO> selectFinishedProductsByCondition(
            @Param("name") String name,
            @Param("type") String type
    );

    @Update("<script>" +
            "UPDATE product " +
            "<set>" +
            "   <if test='productName != null'>product_name = #{productName},</if>" +
            "   <if test='productType != null'>product_type = #{productType},</if>" +
            "   <if test='status != null'>status = #{status},</if>" +
            "   <if test='packagingMethod != null'>packaging_method = #{packagingMethod},</if>" +
            "   <if test='weightPerPiece != null'>weight_per_piece = #{weightPerPiece},</if>" +
            "   <if test='piecesPerPallet != null'>pieces_per_pallet = #{piecesPerPallet},</if>" +
            "   <if test='screenMeshId != null'>screen_mesh_id = #{screenMeshId},</if>" +
            "   <if test='canStack != null'>can_stack = #{canStack},</if>" +
            "   updated_by = #{updatedBy}, " +
            "   updated_at = #{updatedAt} " +
            "</set>" +
            "WHERE id = #{productId}" +
            "</script>")
    int dynamicUpdate(
            @Param("productId") Integer productId,
            @Param("productName") String productName,
            @Param("productType") String productType,
            @Param("status") String status,
            @Param("packagingMethod") String packagingMethod,
            @Param("weightPerPiece") BigDecimal weightPerPiece,
            @Param("piecesPerPallet") Integer piecesPerPallet,
            @Param("screenMeshId") Integer screenMeshId,
            @Param("updatedBy") Integer updatedBy,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("canStack") Boolean canStack
    );

    @Delete("DELETE FROM product WHERE id = #{id}")
    @Override
    int deleteById(Serializable id);

    @Select("SELECT warehouse_id, warehouse_name,product_id,product_name," +
            "sum(total_pieces) AS totalPieces, sum(total_quantity) AS totalQuantity " +
            "FROM v_warehouse_inventory_summary WHERE product_id = #{id} " +
            "GROUP BY warehouse_id")
    List<VInventorySummary> getProductWarehouse(Integer id);
}
