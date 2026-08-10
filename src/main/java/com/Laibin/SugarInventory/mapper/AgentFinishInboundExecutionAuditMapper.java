package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionAudit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface AgentFinishInboundExecutionAuditMapper {
    @Insert("INSERT INTO agent_finish_inbound_execution_audit "
            + "(confirmation_ref, execution_ref, owner_user_id, agent_session_id, event_type, "
            + "from_status, to_status, result_code, error_code, details_sha256, created_at) VALUES "
            + "(#{confirmationRef}, #{executionRef}, #{ownerUserId}, #{agentSessionId}, #{eventType}, "
            + "#{fromStatus}, #{toStatus}, #{resultCode}, #{errorCode}, #{detailsSha256}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AgentFinishInboundExecutionAudit audit);
}
