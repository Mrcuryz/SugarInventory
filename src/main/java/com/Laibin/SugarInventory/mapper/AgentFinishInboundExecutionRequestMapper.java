package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentFinishInboundExecutionRequestMapper
        extends BaseMapper<AgentFinishInboundExecutionRequest> {

    @Select("SELECT * FROM agent_finish_inbound_execution_request "
            + "WHERE confirmation_id = #{confirmationId} LIMIT 1 FOR UPDATE")
    AgentFinishInboundExecutionRequest selectByConfirmationForUpdate(
            @Param("confirmationId") Long confirmationId);

    @Select("SELECT * FROM agent_finish_inbound_execution_request "
            + "WHERE id = #{id} LIMIT 1 FOR UPDATE")
    AgentFinishInboundExecutionRequest selectByIdForUpdate(@Param("id") Long id);
}
