package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PalletTaskQueryMapper {

    @Select({
            "<script>",
            "SELECT ",
            " t.id AS taskId, t.task_type AS taskType, t.biz_scene AS bizScene, t.status AS taskStatus,",
            " t.pallet_code_id AS palletCodeId, pc.code AS code,",
            " t.target_warehouse_id AS targetWarehouseId, tw.warehouse_name AS targetWarehouseName, t.target_side AS targetSide,",
            " t.product_id AS productId, p.product_name AS productName, p.product_type AS productType,",
            " t.product_status AS productStatus, t.production_date AS productionDate,",
            " t.screen_mesh_id AS screenMeshId, sm.mesh_name AS screenMeshName,",
            " t.assay_id AS assayId,",
            " t.created_at AS createdAt, cu.name AS createdBy,",
            " t.confirmed_at AS confirmedAt, uu.name AS confirmedBy,",
            " COALESCE(si.cnt, 0) AS semiItemCount",
            " FROM pallet_task t",
            " LEFT JOIN pallet_code pc ON t.pallet_code_id = pc.id",
            " LEFT JOIN product p ON t.product_id = p.id",
            " LEFT JOIN screen_mesh sm ON t.screen_mesh_id = sm.id",
            " LEFT JOIN warehouse tw ON t.target_warehouse_id = tw.id",
            " LEFT JOIN user cu ON t.created_by = cu.id",
            " LEFT JOIN user uu ON t.confirmed_by = uu.id",
            " LEFT JOIN (SELECT pallet_task_id, COUNT(*) AS cnt FROM pallet_task_semi_item GROUP BY pallet_task_id) si",
            "   ON t.id = si.pallet_task_id",
            " WHERE 1=1",
            "   AND t.cycle_no = pc.current_cycle_no",
            " <if test='q.code != null and q.code != \"\"'>",
            "   AND pc.code = #{q.code}",
            " </if>",
            " <if test='q.taskType != null and q.taskType != \"\"'>",
            "   AND t.task_type = #{q.taskType}",
            " </if>",
            " <if test='q.bizScene != null and q.bizScene != \"\"'>",
            "   AND t.biz_scene = #{q.bizScene}",
            " </if>",
            " <if test='q.status != null and q.status != \"\"'>",
            "   AND t.status = #{q.status}",
            " </if>",
            " <if test='q.productName != null and q.productName != \"\"'>",
            "   AND p.product_name LIKE CONCAT('%', #{q.productName}, '%')",
            " </if>",
            " <if test='q.productType != null and q.productType != \"\"'>",
            "   AND p.product_type = #{q.productType}",
            " </if>",
            " <if test='q.productStatus != null and q.productStatus != \"\"'>",
            "   AND t.product_status = #{q.productStatus}",
            " </if>",
            " <if test='q.productionDateStart != null'>",
            "   AND t.production_date &gt;= #{q.productionDateStart}",
            " </if>",
            " <if test='q.productionDateEnd != null'>",
            "   AND t.production_date &lt;= #{q.productionDateEnd}",
            " </if>",
            " ORDER BY t.created_at DESC",
            " LIMIT #{offset}, #{size}",
            "</script>"
    })
    List<PalletTaskPageVO> pageTasks(@Param("q") PalletTaskQueryDTO q,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            " FROM pallet_task t",
            " LEFT JOIN pallet_code pc ON t.pallet_code_id = pc.id",
            " LEFT JOIN product p ON t.product_id = p.id",
            " WHERE 1=1",
            "   AND t.cycle_no = pc.current_cycle_no",
            " <if test='q.code != null and q.code != \"\"'>",
            "   AND pc.code = #{q.code}",
            " </if>",
            " <if test='q.taskType != null and q.taskType != \"\"'>",
            "   AND t.task_type = #{q.taskType}",
            " </if>",
            " <if test='q.bizScene != null and q.bizScene != \"\"'>",
            "   AND t.biz_scene = #{q.bizScene}",
            " </if>",
            " <if test='q.status != null and q.status != \"\"'>",
            "   AND t.status = #{q.status}",
            " </if>",
            " <if test='q.productName != null and q.productName != \"\"'>",
            "   AND p.product_name LIKE CONCAT('%', #{q.productName}, '%')",
            " </if>",
            " <if test='q.productType != null and q.productType != \"\"'>",
            "   AND p.product_type = #{q.productType}",
            " </if>",
            " <if test='q.productStatus != null and q.productStatus != \"\"'>",
            "   AND t.product_status = #{q.productStatus}",
            " </if>",
            " <if test='q.productionDateStart != null'>",
            "   AND t.production_date &gt;= #{q.productionDateStart}",
            " </if>",
            " <if test='q.productionDateEnd != null'>",
            "   AND t.production_date &lt;= #{q.productionDateEnd}",
            " </if>",
            "</script>"
    })
    Long countTasks(@Param("q") PalletTaskQueryDTO q);
}

