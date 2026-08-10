package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentFinishInboundExecutionConfirmationMapper
        extends BaseMapper<AgentFinishInboundExecutionConfirmation> {

    @Select("SELECT * FROM agent_finish_inbound_execution_confirmation "
            + "WHERE confirmation_ref = #{confirmationRef} LIMIT 1 FOR UPDATE")
    AgentFinishInboundExecutionConfirmation selectByRefForUpdate(
            @Param("confirmationRef") String confirmationRef);

    @Select("SELECT * FROM agent_finish_inbound_execution_confirmation "
            + "WHERE preview_ref = #{previewRef} LIMIT 1 FOR UPDATE")
    AgentFinishInboundExecutionConfirmation selectByPreviewRefForUpdate(
            @Param("previewRef") String previewRef);
}
