package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentAssayEntryConfirmation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AgentAssayEntryConfirmationMapper extends BaseMapper<AgentAssayEntryConfirmation> {
    @Select("SELECT * FROM agent_assay_entry_confirmation WHERE preview_ref=#{ref} FOR UPDATE")
    AgentAssayEntryConfirmation selectByPreviewForUpdate(@Param("ref") String ref);
}
