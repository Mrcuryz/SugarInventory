package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.po.ProductionOrderLabelBatch;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelBatchVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductionOrderLabelBatchMapper extends BaseMapper<ProductionOrderLabelBatch> {
    @Select("SELECT batch_no FROM production_order_label_batch WHERE production_order_id = #{orderId} " +
            "ORDER BY id DESC LIMIT 1 FOR UPDATE")
    String selectLatestBatchNoForUpdate(@Param("orderId") Long orderId);

    @Select("SELECT * FROM production_order_label_batch WHERE production_order_id = #{orderId} " +
            "ORDER BY created_at ASC, id ASC FOR UPDATE")
    List<ProductionOrderLabelBatch> listByOrderForUpdate(@Param("orderId") Long orderId);

    @Select("SELECT id, production_order_id AS productionOrderId, order_no AS orderNo, batch_no AS batchNo, " +
            "product_id AS productId, product_name_snapshot AS productName, reserved_count AS reservedCount, " +
            "used_count AS usedCount, recycled_count AS recycledCount, status, printed_at AS printedAt, " +
            "closed_at AS closedAt, created_at AS createdAt, remark " +
            "FROM production_order_label_batch WHERE production_order_id = #{orderId} ORDER BY created_at ASC, id ASC")
    List<ProductionLabelBatchVO> listBatchVOByOrder(@Param("orderId") Long orderId);

    @Update("UPDATE production_order_label_batch b SET " +
            "used_count = (SELECT COUNT(*) FROM production_order_label_code c WHERE c.batch_id = b.id AND c.status = 'USED'), " +
            "recycled_count = (SELECT COUNT(*) FROM production_order_label_code c WHERE c.batch_id = b.id AND c.status = 'RECYCLED'), " +
            "status = CASE " +
            "  WHEN (SELECT COUNT(*) FROM production_order_label_code c WHERE c.batch_id = b.id AND c.status = 'RESERVED') = 0 THEN 'CLOSED' " +
            "  WHEN printed_at IS NOT NULL THEN 'PRINTED' ELSE 'RESERVED' END, " +
            "closed_at = CASE WHEN (SELECT COUNT(*) FROM production_order_label_code c WHERE c.batch_id = b.id AND c.status = 'RESERVED') = 0 THEN COALESCE(closed_at, NOW()) ELSE closed_at END, " +
            "updated_at = NOW() WHERE b.id = #{batchId}")
    int refreshBatchProgress(@Param("batchId") Long batchId);
}
