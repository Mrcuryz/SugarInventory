package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PalletTaskMapper extends BaseMapper<PalletTask> {

    @Select("SELECT COUNT(*) FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND task_type IN ('SEMI_IN','FINISH_IN') " +
            "AND cycle_no = #{cycleNo}")
    long countPendingInTasks(@Param("palletCodeId") Integer palletCodeId,
                             @Param("cycleNo") Integer cycleNo);

    @Select("SELECT * FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND assay_id IS NOT NULL " +
            "AND cycle_no = #{cycleNo} " +
            "ORDER BY id DESC LIMIT 1")
    PalletTask selectLatestWithAssay(@Param("palletCodeId") Integer palletCodeId,
                                     @Param("cycleNo") Integer cycleNo);

    @Select("SELECT * FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND task_type IN ('SEMI_IN','FINISH_IN') " +
            "AND cycle_no = #{cycleNo} " +
            "ORDER BY id DESC LIMIT 1")
    PalletTask selectPendingByCycle(@Param("palletCodeId") Integer palletCodeId,
                                    @Param("cycleNo") Integer cycleNo);

    @Select("SELECT COUNT(*) FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND task_type = 'OUT' " +
            "AND cycle_no = #{cycleNo}")
    long countPendingOutTasks(@Param("palletCodeId") Integer palletCodeId,
                              @Param("cycleNo") Integer cycleNo);

    @Select("SELECT * FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND task_type = 'OUT' " +
            "AND biz_scene = #{bizScene} " +
            "AND status = 'PENDING' " +
            "AND cycle_no = #{cycleNo} " +
            "ORDER BY id DESC LIMIT 1")
    PalletTask selectPendingOutTaskByScene(@Param("palletCodeId") Integer palletCodeId,
                                           @Param("cycleNo") Integer cycleNo,
                                           @Param("bizScene") String bizScene);

    @Select("SELECT COUNT(*) FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND task_type = 'TRANSFER' " +
            "AND cycle_no = #{cycleNo}")
    long countPendingTransferTasks(@Param("palletCodeId") Integer palletCodeId,
                                   @Param("cycleNo") Integer cycleNo);

    @Select("SELECT * FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND task_type = 'TRANSFER' " +
            "AND status = 'PENDING' " +
            "AND cycle_no = #{cycleNo} " +
            "ORDER BY id DESC LIMIT 1")
    PalletTask selectPendingTransferTaskByCycle(@Param("palletCodeId") Integer palletCodeId,
                                                @Param("cycleNo") Integer cycleNo);

    @Select("SELECT * FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND cycle_no < #{currentCycleNo} " +
            "ORDER BY cycle_no ASC, id ASC")
    List<PalletTask> selectPreviousCyclePendingTasks(@Param("palletCodeId") Integer palletCodeId,
                                                     @Param("currentCycleNo") Integer currentCycleNo);

    @Update("UPDATE pallet_task SET status = 'CANCELED', confirmed_by = #{operatorId}, confirmed_at = NOW() " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND cycle_no < #{currentCycleNo}")
    int cancelPreviousCyclePendingTasks(@Param("palletCodeId") Integer palletCodeId,
                                        @Param("currentCycleNo") Integer currentCycleNo,
                                        @Param("operatorId") Integer operatorId);
}
