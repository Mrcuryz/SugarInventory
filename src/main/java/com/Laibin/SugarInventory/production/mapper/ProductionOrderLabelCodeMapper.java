package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderLabelCode;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelCodeVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductionOrderLabelCodeMapper extends BaseMapper<ProductionOrderLabelCode> {
    @Select("SELECT * FROM pallet_code " +
            "WHERE fixed_mode_enabled = 1 AND fixed_product_id = #{productId} AND status = 'FREE' " +
            "ORDER BY updated_at ASC, id ASC LIMIT #{limit} FOR UPDATE")
    List<PalletCode> selectFreeFixedCodesForUpdate(@Param("productId") Integer productId,
                                                   @Param("limit") int limit);

    @Select("SELECT * FROM production_order_label_code WHERE production_order_id = #{orderId} " +
            "ORDER BY product_id ASC, batch_id ASC, sequence_no ASC FOR UPDATE")
    List<ProductionOrderLabelCode> listByOrderForUpdate(@Param("orderId") Long orderId);

    @Select("SELECT * FROM production_order_label_code WHERE production_order_id = #{orderId} " +
            "AND product_id = #{productId} AND status = 'RESERVED' " +
            "ORDER BY batch_id ASC, sequence_no ASC FOR UPDATE")
    List<ProductionOrderLabelCode> listReservedByProductForUpdate(@Param("orderId") Long orderId,
                                                                  @Param("productId") Integer productId);

    @Select("SELECT id, batch_id AS batchId, batch_no AS batchNo, sequence_no AS sequenceNo, " +
            "pallet_code_id AS palletCodeId, pallet_code AS palletCode, product_id AS productId, " +
            "product_name_snapshot AS productName, qr_content AS qrContent, status, used_output_code_id AS usedOutputCodeId, " +
            "used_at AS usedAt, recycled_at AS recycledAt, created_at AS createdAt " +
            "FROM production_order_label_code WHERE production_order_id = #{orderId} ORDER BY batch_id ASC, sequence_no ASC")
    List<ProductionLabelCodeVO> listCodeVOByOrder(@Param("orderId") Long orderId);

    @Select("SELECT * FROM production_order_label_code WHERE id = #{id} AND label_token = #{token} LIMIT 1")
    ProductionOrderLabelCode selectByIdAndToken(@Param("id") Long id, @Param("token") String token);

    @Update("UPDATE production_order_label_code SET status = 'USED', used_output_code_id = #{outputCodeId}, used_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'RESERVED'")
    int markUsed(@Param("id") Long id, @Param("outputCodeId") Long outputCodeId);

    @Update("UPDATE production_order_label_code SET status = 'RECYCLED', recycled_at = NOW(), updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'RESERVED'")
    int markRecycled(@Param("id") Long id);

    @Update("UPDATE production_order_label_batch SET printed_at = COALESCE(printed_at, NOW()), status = CASE WHEN status = 'RESERVED' THEN 'PRINTED' ELSE status END, updated_at = NOW() " +
            "WHERE id = #{batchId} AND status IN ('RESERVED','PRINTED')")
    int markBatchPrinted(@Param("batchId") Long batchId);
}
