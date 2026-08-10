package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_finish_inbound_execution_audit")
public class AgentFinishInboundExecutionAudit {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String confirmationRef;
    private String executionRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private String eventType;
    private String fromStatus;
    private String toStatus;
    private String resultCode;
    private String errorCode;
    private String detailsSha256;
    private LocalDateTime createdAt;
}
