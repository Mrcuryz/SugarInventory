package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOutputCodeVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductionOrderOutputCodeMapper extends BaseMapper<ProductionOrderOutputCode> {
    @Select("SELECT id, production_order_id AS productionOrderId, order_no AS orderNo, output_id AS outputId, " +
            "pallet_code_id AS palletCodeId, label_code_id AS labelCodeId, pallet_code AS palletCode, product_id AS productId, product_name_snapshot AS productName, " +
            "quantity, unit, pieces, pallet_task_id AS palletTaskId, inventory_id AS inventoryId, status, " +
            "printed_at AS printedAt, inbound_at AS inboundAt, created_at AS createdAt, remark " +
            "FROM production_order_output_code WHERE production_order_id = #{orderId} AND status != 'CANCELED' ORDER BY id DESC")
    List<ProductionOutputCodeVO> listCodesByOrder(@Param("orderId") Long orderId);

    @Select("SELECT id, production_order_id AS productionOrderId, order_no AS orderNo, output_id AS outputId, " +
            "pallet_code_id AS palletCodeId, label_code_id AS labelCodeId, pallet_code AS palletCode, product_id AS productId, product_name_snapshot AS productName, " +
            "quantity, unit, pieces, pallet_task_id AS palletTaskId, inventory_id AS inventoryId, status, " +
            "printed_at AS printedAt, inbound_at AS inboundAt, created_at AS createdAt, remark " +
            "FROM production_order_output_code WHERE output_id = #{outputId} AND status != 'CANCELED' ORDER BY id ASC")
    List<ProductionOutputCodeVO> listCodesByOutput(@Param("outputId") Long outputId);

    @Select("SELECT * FROM production_order_output_code WHERE pallet_task_id = #{taskId} AND status != 'CANCELED' LIMIT 1")
    ProductionOrderOutputCode selectByTaskId(@Param("taskId") Integer taskId);

    @Select("SELECT * FROM pallet_code " +
            "WHERE fixed_mode_enabled = 1 AND fixed_product_id = #{productId} AND status = 'FREE' " +
            "ORDER BY updated_at ASC, id ASC LIMIT #{limit} FOR UPDATE")
    List<PalletCode> selectFreeFixedCodesForUpdate(@Param("productId") Integer productId,
                                                   @Param("limit") int limit);

    @Update("UPDATE production_order_output_code SET status = 'INSTOCK', inventory_id = #{inventoryId}, inbound_at = NOW() " +
            "WHERE id = #{id} AND status != 'CANCELED'")
    int markInstock(@Param("id") Long id, @Param("inventoryId") Integer inventoryId);

    @Update("UPDATE production_order_output_code SET printed_at = NOW() WHERE output_id = #{outputId} AND status != 'CANCELED'")
    int markPrintedByOutput(@Param("outputId") Long outputId);
}
