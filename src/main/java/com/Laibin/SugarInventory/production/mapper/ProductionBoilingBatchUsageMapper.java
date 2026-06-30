package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.po.ProductionBoilingBatchUsage;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchUsageVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductionBoilingBatchUsageMapper extends BaseMapper<ProductionBoilingBatchUsage> {
    @Select("SELECT COALESCE(SUM(bucket_quantity), 0) FROM production_boiling_batch_usage " +
            "WHERE batch_id = #{batchId} AND status = #{status}")
    BigDecimal sumBucketByStatus(@Param("batchId") Long batchId, @Param("status") String status);

    @Select("SELECT COALESCE(SUM(weight_kg), 0) FROM production_boiling_batch_usage " +
            "WHERE batch_id = #{batchId} AND status = #{status}")
    BigDecimal sumWeightByStatus(@Param("batchId") Long batchId, @Param("status") String status);

    @Select("SELECT u.id, u.batch_id AS batchId, u.batch_no AS batchNo, u.production_order_id AS productionOrderId, " +
            "u.order_no AS orderNo, o.order_type AS orderType, o.status AS orderStatus, u.usage_unit AS usageUnit, " +
            "u.usage_quantity AS usageQuantity, u.bucket_quantity AS bucketQuantity, u.weight_kg AS weightKg, " +
            "u.status, u.remark, u.created_by_name AS createdByName, u.created_at AS createdAt, u.updated_at AS updatedAt " +
            "FROM production_boiling_batch_usage u " +
            "LEFT JOIN production_order o ON u.production_order_id = o.id " +
            "WHERE u.batch_id = #{batchId} ORDER BY u.created_at DESC, u.id DESC")
    List<ProductionBoilingBatchUsageVO> listByBatch(@Param("batchId") Long batchId);

    @Select("SELECT u.id, u.batch_id AS batchId, u.batch_no AS batchNo, u.production_order_id AS productionOrderId, " +
            "u.order_no AS orderNo, o.order_type AS orderType, o.status AS orderStatus, u.usage_unit AS usageUnit, " +
            "u.usage_quantity AS usageQuantity, u.bucket_quantity AS bucketQuantity, u.weight_kg AS weightKg, " +
            "u.status, u.remark, u.created_by_name AS createdByName, u.created_at AS createdAt, u.updated_at AS updatedAt " +
            "FROM production_boiling_batch_usage u " +
            "LEFT JOIN production_order o ON u.production_order_id = o.id " +
            "WHERE u.production_order_id = #{orderId} AND u.status != 'CANCELED' ORDER BY u.id ASC")
    List<ProductionBoilingBatchUsageVO> listByOrder(@Param("orderId") Long orderId);

    @Select("SELECT * FROM production_boiling_batch_usage " +
            "WHERE production_order_id = #{orderId} AND status = 'RESERVED' FOR UPDATE")
    List<ProductionBoilingBatchUsage> listReservedByOrderForUpdate(@Param("orderId") Long orderId);

    @Update("UPDATE production_boiling_batch_usage SET status = #{status}, updated_at = NOW() " +
            "WHERE production_order_id = #{orderId} AND status = 'RESERVED'")
    int updateReservedByOrder(@Param("orderId") Long orderId, @Param("status") String status);
}
