package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PalletTaskMapper extends BaseMapper<PalletTask> {

    @Select("SELECT COUNT(*) FROM pallet_task " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND status = 'PENDING' " +
            "AND task_type IN ('SEMI_IN','FINISH_IN')")
    long countPendingInTasks(@Param("palletCodeId") Integer palletCodeId);

    @Select("SELECT * FROM pallet_task WHERE pallet_code_id = #{palletCodeId} AND assay_id IS NOT NULL ORDER BY id DESC LIMIT 1")
    PalletTask selectLatestWithAssay(@Param("palletCodeId") Integer palletCodeId);
}
