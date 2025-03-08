package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.ProductInfoVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
public interface ProductMapper extends BaseMapper<Product> {

    @Select("SELECT * FROM product WHERE product_name = #{name}")
    Product selectByName(@Param("name") String productName);

    // 查询产品名称是否已存在
    @Select("SELECT COUNT(*) FROM product WHERE product_name = #{name}")
    boolean existsByName(@Param("name") String name);

    // 根据名称查询产品
    @Select("<script>" +
            "SELECT * FROM product " +
            "<if test='name != null and name != \"\"'>" +
            "   WHERE product_name LIKE CONCAT('%', #{name}, '%') " +
            "</if>" +
            "</script>")
    List<Product> selectProductsByName(
            @Param("name") String name
    );

    // 查询所有半成品名称
    @Select("SELECT DISTINCT id AS productId, product_name AS productName " +
            "FROM product WHERE status = '半成品'")
    List<ProductInfoVO> selectSemiProductNames();

    // 根据名称或类型查询半成品
    @Select("<script>" +
            "SELECT id AS productId, product_name AS productName " +
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

    // 查询所有成品名称
    @Select("SELECT DISTINCT id AS productId, product_name AS productName " +
            "FROM product WHERE status = '成品'")
    List<ProductInfoVO> selectFinishedProductNames();

    // 根据名称或类型查询半成品
    @Select("<script>" +
            "SELECT id AS productId, product_name AS productName " +
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

    // 根据条件动态更新对应id的产品信息
    @Update("<script>" +
            "UPDATE product " +
            "<set>" +
            "   <if test='productName != null'>product_name = #{productName},</if>" +
            "   <if test='productType != null'>product_type = #{productType},</if>" +
            "   <if test='status != null'>status = #{status},</if>" +
            "   <if test='packagingMethod != null'>packaging_method = #{packagingMethod},</if>" +
            "   <if test='weightPerPiece != null'>weight_per_piece = #{weightPerPiece},</if>" +
            "   <if test='piecesPerPallet != null'>pieces_per_pallet = #{piecesPerPallet},</if>" +
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
            @Param("updatedBy") Integer updatedBy,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("canStack") Boolean canStack
    );

    // 根据id删除产品
    @Delete("DELETE FROM product WHERE id = #{id}")
    @Override
    int deleteById(Serializable id);
}
