package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 托盘码 Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-12-08
 */
@Mapper
public interface PalletCodeMapper extends BaseMapper<PalletCode> {

    @Select("SELECT * FROM pallet_code WHERE id = #{id} LIMIT 1 FOR UPDATE")
    PalletCode selectByIdForUpdate(@Param("id") Integer id);
}

