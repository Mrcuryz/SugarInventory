package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.SemiPreparePool;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SemiPreparePoolMapper extends BaseMapper<SemiPreparePool> {

    @Select("SELECT * FROM semi_prepare_pool " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND cycle_no = #{cycleNo} " +
            "AND status = 'ACTIVE' " +
            "LIMIT 1")
    SemiPreparePool selectActiveByPalletAndCycle(@Param("palletCodeId") Integer palletCodeId,
                                                 @Param("cycleNo") Integer cycleNo);

    @Select("SELECT * FROM semi_prepare_pool " +
            "WHERE pallet_code_id = #{palletCodeId} " +
            "AND cycle_no = #{cycleNo} " +
            "AND status = 'ACTIVE' " +
            "LIMIT 1 FOR UPDATE")
    SemiPreparePool selectActiveByPalletAndCycleForUpdate(@Param("palletCodeId") Integer palletCodeId,
                                                          @Param("cycleNo") Integer cycleNo);
}
