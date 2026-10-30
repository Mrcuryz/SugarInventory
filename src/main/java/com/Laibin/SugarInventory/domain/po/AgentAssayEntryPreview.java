package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_assay_entry_preview")
public class AgentAssayEntryPreview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String previewRef;
    private Integer ownerUserId;
    private String agentSessionId;
    private String status;
    private String normalizedInputJson;
    private String stateSnapshotJson;
    private String stateDigest;
    private String publicPayloadJson;
    private String contentDigest;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime updatedAt;
}
