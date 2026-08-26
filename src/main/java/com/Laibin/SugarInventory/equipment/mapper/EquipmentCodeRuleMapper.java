package com.Laibin.SugarInventory.equipment.mapper;

import com.Laibin.SugarInventory.equipment.domain.po.EquipmentCodeRulePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface EquipmentCodeRuleMapper extends BaseMapper<EquipmentCodeRulePO> {
    @Select("SELECT * FROM equipment_code_rule WHERE unit_id=#{unitId} AND category_id=#{categoryId} " +
            "AND enabled=1 ORDER BY id FOR UPDATE")
    List<EquipmentCodeRulePO> selectEnabledForUpdate(@Param("unitId") Integer unitId,
                                                      @Param("categoryId") Integer categoryId);

    @Update("UPDATE equipment_code_rule SET next_sequence=next_sequence+1, version=version+1, updated_at=NOW() " +
            "WHERE id=#{id} AND next_sequence=#{expectedSequence}")
    int advance(@Param("id") Integer id, @Param("expectedSequence") Integer expectedSequence);
}
