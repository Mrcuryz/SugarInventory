package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutput;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductionOrderOutputMapper extends BaseMapper<ProductionOrderOutput> {
    @Select("SELECT id, production_order_id AS productionOrderId, order_no AS orderNo, product_id AS productId, " +
            "product_name_snapshot AS productName, product_status AS productStatus, production_date AS productionDate, " +
            "board_count AS boardCount, piece_count AS pieceCount, total_pieces AS totalPieces, pieces_per_pallet AS piecesPerPallet, " +
            "total_weight AS totalWeight, required_qr_count AS requiredQrCount, bound_qr_count AS boundQrCount, " +
            "inbound_qr_count AS inboundQrCount, status, created_at AS createdAt, remark " +
            "FROM production_order_output WHERE production_order_id = #{orderId} AND status != 'CANCELED' ORDER BY id DESC")
    List<ProductionOutputVO> listOutputs(@Param("orderId") Long orderId);

    @Update("UPDATE production_order_output o SET " +
            "bound_qr_count = (SELECT COUNT(*) FROM production_order_output_code c WHERE c.output_id = o.id AND c.status != 'CANCELED'), " +
            "inbound_qr_count = (SELECT COUNT(*) FROM production_order_output_code c WHERE c.output_id = o.id AND c.status = 'INSTOCK'), " +
            "status = CASE " +
            "    WHEN (SELECT COUNT(*) FROM production_order_output_code c WHERE c.output_id = o.id AND c.status = 'INSTOCK') >= o.required_qr_count AND o.required_qr_count > 0 THEN 'INSTOCK' " +
            "    WHEN (SELECT COUNT(*) FROM production_order_output_code c WHERE c.output_id = o.id AND c.status = 'INSTOCK') > 0 THEN 'PART_INBOUND' " +
            "    WHEN (SELECT COUNT(*) FROM production_order_output_code c WHERE c.output_id = o.id AND c.status != 'CANCELED') >= o.required_qr_count AND o.required_qr_count > 0 THEN 'BOUND' " +
            "    ELSE o.status END, " +
            "updated_at = NOW() WHERE o.id = #{outputId}")
    int refreshOutputProgress(@Param("outputId") Long outputId);
}
