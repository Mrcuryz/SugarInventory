package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchQueryDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionBoilingBatch;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductionBoilingBatchMapper extends BaseMapper<ProductionBoilingBatch> {
    @Select("SELECT batch_no FROM production_boiling_batch " +
            "WHERE batch_no LIKE CONCAT(#{prefix}, '%') " +
            "ORDER BY batch_no DESC LIMIT 1 FOR UPDATE")
    String selectLatestBatchNoForUpdate(@Param("prefix") String prefix);

    @Select("<script>" +
            "SELECT b.id, b.batch_no AS batchNo, b.boiling_date AS boilingDate, b.team_name AS teamName, " +
            "b.sugar_type AS sugarType, b.product_id AS productId, b.product_name_snapshot AS productName, " +
            "b.pot_count AS potCount, b.bucket_count AS bucketCount, b.kg_per_bucket AS kgPerBucket, " +
            "b.total_weight_kg AS totalWeightKg, b.status, b.source_text AS sourceText, b.remark, " +
            "b.created_by_name AS createdByName, b.created_at AS createdAt, b.updated_at AS updatedAt, " +
            "(SELECT COALESCE(SUM(u.bucket_quantity), 0) FROM production_boiling_batch_usage u WHERE u.batch_id = b.id AND u.status = 'RESERVED') AS reservedBucketCount, " +
            "(SELECT COALESCE(SUM(u.weight_kg), 0) FROM production_boiling_batch_usage u WHERE u.batch_id = b.id AND u.status = 'RESERVED') AS reservedWeightKg, " +
            "(SELECT COALESCE(SUM(u.bucket_quantity), 0) FROM production_boiling_batch_usage u WHERE u.batch_id = b.id AND u.status = 'CONSUMED') AS consumedBucketCount, " +
            "(SELECT COALESCE(SUM(u.weight_kg), 0) FROM production_boiling_batch_usage u WHERE u.batch_id = b.id AND u.status = 'CONSUMED') AS consumedWeightKg " +
            "FROM production_boiling_batch b " +
            "WHERE 1=1 " +
            "<if test='query.batchNo != null and query.batchNo != \"\"'>AND b.batch_no LIKE CONCAT('%', #{query.batchNo}, '%') </if>" +
            "<if test='query.productQuery != null and query.productQuery != \"\"'>AND (b.product_name_snapshot LIKE CONCAT('%', #{query.productQuery}, '%') OR b.sugar_type LIKE CONCAT('%', #{query.productQuery}, '%')) </if>" +
            "<if test='query.status != null and query.status != \"\"'>AND b.status = #{query.status} </if>" +
            "<if test='query.startDate != null'>AND b.boiling_date &gt;= #{query.startDate} </if>" +
            "<if test='query.endDate != null'>AND b.boiling_date &lt;= #{query.endDate} </if>" +
            "ORDER BY b.boiling_date DESC, b.id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<ProductionBoilingBatchVO> pageBatches(@Param("query") ProductionBoilingBatchQueryDTO query,
                                               @Param("offset") int offset,
                                               @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM production_boiling_batch b WHERE 1=1 " +
            "<if test='query.batchNo != null and query.batchNo != \"\"'>AND b.batch_no LIKE CONCAT('%', #{query.batchNo}, '%') </if>" +
            "<if test='query.productQuery != null and query.productQuery != \"\"'>AND (b.product_name_snapshot LIKE CONCAT('%', #{query.productQuery}, '%') OR b.sugar_type LIKE CONCAT('%', #{query.productQuery}, '%')) </if>" +
            "<if test='query.status != null and query.status != \"\"'>AND b.status = #{query.status} </if>" +
            "<if test='query.startDate != null'>AND b.boiling_date &gt;= #{query.startDate} </if>" +
            "<if test='query.endDate != null'>AND b.boiling_date &lt;= #{query.endDate} </if>" +
            "</script>")
    Long countBatches(@Param("query") ProductionBoilingBatchQueryDTO query);
}
