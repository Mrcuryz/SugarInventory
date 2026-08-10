package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_finish_inbound_execution_request")
public class AgentFinishInboundExecutionRequest {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String executionRef;
    private Long confirmationId;
    private String confirmationRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private String idempotencyKeySha256;
    private String requestSha256;
    private String status;
    private Integer attemptCount;
    private String resultCode;
    private String resultJson;
    private String errorCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
