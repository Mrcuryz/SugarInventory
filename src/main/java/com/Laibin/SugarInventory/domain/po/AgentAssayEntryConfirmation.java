package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_assay_entry_confirmation")
public class AgentAssayEntryConfirmation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String confirmationRef;
    private String previewRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private String contentDigest;
    private String tokenDigest;
    private String idempotencyDigest;
    private String status;
    private LocalDateTime confirmedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private Integer assayId;
    private String resultJson;
}
