package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.SemiPreparePoolBalance;
import com.Laibin.SugarInventory.domain.vo.SemiPreparePoolBalanceVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface SemiPreparePoolBalanceMapper extends BaseMapper<SemiPreparePoolBalance> {

    @Select("SELECT * FROM semi_prepare_pool_balance WHERE id = #{id} LIMIT 1 FOR UPDATE")
    SemiPreparePoolBalance selectByIdForUpdate(@Param("id") Long id);

    @Select({
            "<script>",
            "SELECT * FROM semi_prepare_pool_balance",
            " WHERE product_id = #{productId}",
            "<choose>",
            " <when test='productionDate != null'>AND production_date = #{productionDate}</when>",
            " <otherwise>AND production_date IS NULL</otherwise>",
            "</choose>",
            "<choose>",
            " <when test='screenMeshId != null'>AND screen_mesh_id = #{screenMeshId}</when>",
            " <otherwise>AND screen_mesh_id IS NULL</otherwise>",
            "</choose>",
            "<choose>",
            " <when test='assayId != null'>AND assay_id = #{assayId}</when>",
            " <otherwise>AND assay_id IS NULL</otherwise>",
            "</choose>",
            " LIMIT 1 FOR UPDATE",
            "</script>"
    })
    SemiPreparePoolBalance selectByBatchForUpdate(@Param("productId") Integer productId,
                                                  @Param("productionDate") LocalDate productionDate,
                                                  @Param("screenMeshId") Integer screenMeshId,
                                                  @Param("assayId") Integer assayId);

    @Select({
            "<script>",
            "SELECT * FROM semi_prepare_pool_balance",
            " WHERE product_id = #{productId}",
            " AND production_date = #{productionDate}",
            " AND remaining_pieces &gt; 0",
            " ORDER BY first_in_at ASC, id ASC",
            " FOR UPDATE",
            "</script>"
    })
    List<SemiPreparePoolBalance> selectActiveByProductDateForUpdate(@Param("productId") Integer productId,
                                                                    @Param("productionDate") LocalDate productionDate);

    @Select({
            "<script>",
            "SELECT COALESCE(SUM(remaining_pieces), 0) FROM semi_prepare_pool_balance",
            " WHERE product_id = #{productId}",
            " AND production_date = #{productionDate}",
            " AND remaining_pieces &gt; 0",
            "</script>"
    })
    Integer sumRemainingByProductDate(@Param("productId") Integer productId,
                                      @Param("productionDate") LocalDate productionDate);

    @Select({
            "<script>",
            "SELECT",
            " b.id AS id,",
            " b.product_id AS productId,",
            " b.product_name_snapshot AS productName,",
            " b.production_date AS productionDate,",
            " b.screen_mesh_id AS screenMeshId,",
            " sm.mesh_name AS screenMeshName,",
            " b.assay_id AS assayId,",
            " b.in_pieces AS inPieces,",
            " b.consumed_pieces AS consumedPieces,",
            " b.remaining_pieces AS remainingPieces,",
            " b.pieces_per_pallet AS piecesPerPallet,",
            " b.weight_per_piece AS weightPerPiece,",
            " b.remaining_weight AS remainingWeight,",
            " b.status AS status,",
            " b.first_in_at AS firstInAt,",
            " b.last_in_at AS lastInAt,",
            " b.last_consumed_at AS lastConsumedAt",
            " FROM semi_prepare_pool_balance b",
            " LEFT JOIN product p ON p.id = b.product_id",
            " LEFT JOIN screen_mesh sm ON sm.id = b.screen_mesh_id",
            " WHERE b.remaining_pieces &gt; 0",
            "<if test='productName != null and productName != \"\"'>",
            " AND b.product_name_snapshot LIKE CONCAT('%', #{productName}, '%')",
            "</if>",
            "<if test='productType != null and productType != \"\"'>",
            " AND p.product_type = #{productType}",
            "</if>",
            "<if test='screenMeshId != null'>",
            " AND b.screen_mesh_id = #{screenMeshId}",
            "</if>",
            "<if test='productionDateStart != null'>",
            " AND b.production_date &gt;= #{productionDateStart}",
            "</if>",
            "<if test='productionDateEnd != null'>",
            " AND b.production_date &lt;= #{productionDateEnd}",
            "</if>",
            " ORDER BY b.last_in_at DESC, b.id DESC",
            " LIMIT #{offset}, #{size}",
            "</script>"
    })
    List<SemiPreparePoolBalanceVO> pageActiveBalances(@Param("productName") String productName,
                                                      @Param("productType") String productType,
                                                      @Param("screenMeshId") Integer screenMeshId,
                                                      @Param("productionDateStart") LocalDate productionDateStart,
                                                      @Param("productionDateEnd") LocalDate productionDateEnd,
                                                      @Param("offset") long offset,
                                                      @Param("size") int size);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            " FROM semi_prepare_pool_balance b",
            " LEFT JOIN product p ON p.id = b.product_id",
            " WHERE b.remaining_pieces &gt; 0",
            "<if test='productName != null and productName != \"\"'>",
            " AND b.product_name_snapshot LIKE CONCAT('%', #{productName}, '%')",
            "</if>",
            "<if test='productType != null and productType != \"\"'>",
            " AND p.product_type = #{productType}",
            "</if>",
            "<if test='screenMeshId != null'>",
            " AND b.screen_mesh_id = #{screenMeshId}",
            "</if>",
            "<if test='productionDateStart != null'>",
            " AND b.production_date &gt;= #{productionDateStart}",
            "</if>",
            "<if test='productionDateEnd != null'>",
            " AND b.production_date &lt;= #{productionDateEnd}",
            "</if>",
            "</script>"
    })
    Long countActiveBalances(@Param("productName") String productName,
                             @Param("productType") String productType,
                             @Param("screenMeshId") Integer screenMeshId,
                             @Param("productionDateStart") LocalDate productionDateStart,
                             @Param("productionDateEnd") LocalDate productionDateEnd);
}
