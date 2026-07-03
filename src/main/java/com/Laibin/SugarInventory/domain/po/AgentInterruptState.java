package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_interrupt_state")
public class AgentInterruptState {
    @TableId(value = "interrupt_id", type = IdType.INPUT)
    private String interruptId;
    private String agentSessionId;
    private Integer userId;
    private String messageId;
    private String kind;
    private String status;
    private String resumeAction;
    private String optionId;
    private String previewId;
    private String clientRequestId;
    private String resultCode;
    private String errorCode;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime resumedAt;
    private LocalDateTime updatedAt;
}
