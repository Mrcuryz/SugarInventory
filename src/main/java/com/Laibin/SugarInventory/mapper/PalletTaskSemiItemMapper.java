package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.PalletTaskSemiItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface PalletTaskSemiItemMapper extends BaseMapper<PalletTaskSemiItem> {

    // 全量覆盖策略：先删后插
    @Delete("DELETE FROM pallet_task_semi_item WHERE pallet_task_id = #{taskId}")
    void deleteByTaskId(@Param("taskId") Integer taskId);

    @Insert({
            "<script>",
            "INSERT INTO pallet_task_semi_item (pallet_task_id, semi_pallet_code_id, prepare_balance_id, semi_product_id, production_date, quantity, unit, board_count, piece_count, total_pieces, use_assay) VALUES ",
            "<foreach collection='items' item='item' separator=','>",
            "(#{item.palletTaskId}, #{item.semiPalletCodeId}, #{item.prepareBalanceId}, #{item.semiProductId}, #{item.productionDate}, #{item.quantity}, #{item.unit}, #{item.boardCount}, #{item.pieceCount}, #{item.totalPieces}, #{item.useAssay})",
            "</foreach>",
            "</script>"
    })
    void batchInsert(@Param("items") List<PalletTaskSemiItem> items);
}
