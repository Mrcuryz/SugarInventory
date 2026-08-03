package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidateQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialQueryDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderMaterial;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidateVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductionOrderMaterialMapper extends BaseMapper<ProductionOrderMaterial> {
    @Select("<script>" +
            "SELECT i.id AS inventoryId, pc.id AS palletCodeId, pc.code AS palletCode, " +
            "       i.product_id AS productId, p.product_name AS productName, i.product_status AS productStatus, " +
            "       COALESCE(pc.production_date, i.entry_date) AS productionDate, " +
            "       CASE WHEN COALESCE(i.pieces, 0) &gt; 0 THEN CONCAT(i.pieces, '件') ELSE CONCAT(COALESCE(i.quantity, 1), '板') END AS quantityText, " +
            "       CASE WHEN COALESCE(i.pieces, 0) &gt; 0 THEN i.pieces ELSE COALESCE(i.quantity, 1) END AS quantity, " +
            "       CASE WHEN COALESCE(i.pieces, 0) &gt; 0 THEN '1' ELSE '0' END AS unit, " +
            "       COALESCE(i.pieces, 0) AS pieces, i.warehouse_id AS warehouseId, w.warehouse_name AS warehouseName, " +
            "       i.side, i.`row_number` AS rowNumber, i.layer, " +
            "       p.weight_per_piece * CASE WHEN COALESCE(i.pieces, 0) &gt; 0 THEN i.pieces ELSE COALESCE(i.quantity, 1) * p.pieces_per_pallet END AS weight " +
            "FROM inventory i " +
            "INNER JOIN pallet_code pc ON i.pallet_code_id = pc.id " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "LEFT JOIN warehouse w ON i.warehouse_id = w.id " +
            "WHERE i.product_status = '半成品' " +
            "<if test='query.productId != null'>AND i.product_id = #{query.productId} </if>" +
            "<if test='query.productionDate != null'>AND COALESCE(pc.production_date, i.entry_date) = #{query.productionDate} </if>" +
            "<if test='query.palletCode != null and query.palletCode != \"\"'>AND pc.code LIKE CONCAT('%', #{query.palletCode}, '%') </if>" +
            "<if test='query.warehouseId != null'>AND i.warehouse_id = #{query.warehouseId} </if>" +
            "ORDER BY COALESCE(pc.production_date, i.entry_date) ASC, pc.id ASC LIMIT #{offset}, #{size}" +
            "</script>")
    List<ProductionMaterialCandidateVO> pageMaterialCandidates(@Param("query") ProductionMaterialCandidateQueryDTO query,
                                                               @Param("offset") int offset,
                                                               @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM inventory i " +
            "INNER JOIN pallet_code pc ON i.pallet_code_id = pc.id " +
            "WHERE i.product_status = '半成品' " +
            "<if test='query.productId != null'>AND i.product_id = #{query.productId} </if>" +
            "<if test='query.productionDate != null'>AND COALESCE(pc.production_date, i.entry_date) = #{query.productionDate} </if>" +
            "<if test='query.palletCode != null and query.palletCode != \"\"'>AND pc.code LIKE CONCAT('%', #{query.palletCode}, '%') </if>" +
            "<if test='query.warehouseId != null'>AND i.warehouse_id = #{query.warehouseId} </if>" +
            "</script>")
    Long countMaterialCandidates(@Param("query") ProductionMaterialCandidateQueryDTO query);

    @Select("SELECT id, production_order_id AS productionOrderId, order_no AS orderNo, pallet_code_id AS palletCodeId, " +
            "pallet_code AS palletCode, product_id AS productId, product_name_snapshot AS productName, product_status AS productStatus, " +
            "production_date AS productionDate, warehouse_id AS warehouseId, warehouse_name_snapshot AS warehouseName, " +
            "side, `row_number` AS rowNumber, layer, quantity, unit, pieces, total_pieces AS totalPieces, total_weight AS totalWeight, " +
            "status, picked_by_name AS pickedByName, picked_at AS pickedAt, remark " +
            "FROM production_order_material WHERE production_order_id = #{orderId} AND status = 'PICKED' ORDER BY picked_at DESC, id DESC")
    List<ProductionMaterialVO> listMaterials(@Param("orderId") Long orderId);

    @Select("<script>" +
            "SELECT m.id, m.production_order_id AS productionOrderId, m.order_no AS orderNo, o.status AS orderStatus, " +
            "       m.pallet_code_id AS palletCodeId, m.pallet_code AS palletCode, m.product_id AS productId, " +
            "       m.product_name_snapshot AS productName, m.product_status AS productStatus, m.production_date AS productionDate, " +
            "       m.warehouse_id AS warehouseId, m.warehouse_name_snapshot AS warehouseName, m.side, m.`row_number` AS rowNumber, " +
            "       m.layer, m.quantity, m.unit, m.pieces, m.total_pieces AS totalPieces, m.total_weight AS totalWeight, " +
            "       m.status, m.picked_by_name AS pickedByName, m.picked_at AS pickedAt, m.remark " +
            "FROM production_order_material m " +
            "INNER JOIN production_order o ON m.production_order_id = o.id " +
            "LEFT JOIN product p ON m.product_id = p.id " +
            "WHERE m.status = 'PICKED' " +
            "  AND m.product_status = '半成品' " +
            "  AND o.status NOT IN ('CANCELED', 'COMPLETED') " +
            "<if test='query.productName != null and query.productName != \"\"'>AND m.product_name_snapshot LIKE CONCAT('%', #{query.productName}, '%') </if>" +
            "<if test='query.productType != null and query.productType != \"\"'>AND p.product_type = #{query.productType} </if>" +
            "<if test='query.screenMeshId != null'>AND p.screen_mesh_id = #{query.screenMeshId} </if>" +
            "<if test='query.productionDateStart != null'>AND m.production_date &gt;= #{query.productionDateStart} </if>" +
            "<if test='query.productionDateEnd != null'>AND m.production_date &lt;= #{query.productionDateEnd} </if>" +
            "ORDER BY m.picked_at DESC, m.id DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<ProductionMaterialVO> pageInProcessMaterials(@Param("query") ProductionInProcessMaterialQueryDTO query,
                                                      @Param("offset") int offset,
                                                      @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM production_order_material m " +
            "INNER JOIN production_order o ON m.production_order_id = o.id " +
            "LEFT JOIN product p ON m.product_id = p.id " +
            "WHERE m.status = 'PICKED' " +
            "  AND m.product_status = '半成品' " +
            "  AND o.status NOT IN ('CANCELED', 'COMPLETED') " +
            "<if test='query.productName != null and query.productName != \"\"'>AND m.product_name_snapshot LIKE CONCAT('%', #{query.productName}, '%') </if>" +
            "<if test='query.productType != null and query.productType != \"\"'>AND p.product_type = #{query.productType} </if>" +
            "<if test='query.screenMeshId != null'>AND p.screen_mesh_id = #{query.screenMeshId} </if>" +
            "<if test='query.productionDateStart != null'>AND m.production_date &gt;= #{query.productionDateStart} </if>" +
            "<if test='query.productionDateEnd != null'>AND m.production_date &lt;= #{query.productionDateEnd} </if>" +
            "</script>")
    Long countInProcessMaterials(@Param("query") ProductionInProcessMaterialQueryDTO query);
}
