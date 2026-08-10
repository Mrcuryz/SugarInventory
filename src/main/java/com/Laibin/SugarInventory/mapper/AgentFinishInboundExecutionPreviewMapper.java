package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentFinishInboundExecutionPreviewMapper extends BaseMapper<AgentFinishInboundExecutionPreview> {
    @Select("SELECT * FROM agent_finish_inbound_execution_preview "
            + "WHERE preview_ref = #{previewRef} LIMIT 1 FOR UPDATE")
    AgentFinishInboundExecutionPreview selectByRefForUpdate(@Param("previewRef") String previewRef);
}
