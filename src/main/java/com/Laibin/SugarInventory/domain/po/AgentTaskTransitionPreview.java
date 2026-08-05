package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_task_transition_preview")
public class AgentTaskTransitionPreview {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String previewRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private Integer previewVersion;
    private String transitionType;
    private String status;
    private String requiredPermissions;
    private String entityRefsJson;
    private String payloadJson;
    private String stateDigest;
    private String normalizedRequestSha256;
    private String contentSha256;
    private LocalDateTime previewedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime revokedAt;
    private LocalDateTime consumedAt;
}
