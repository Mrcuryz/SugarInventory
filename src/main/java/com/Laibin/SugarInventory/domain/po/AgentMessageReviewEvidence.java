package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message_review_evidence")
public class AgentMessageReviewEvidence {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long reviewId;
    private String evidenceType;
    private String refId;
    private String evidenceSummary;
    private LocalDateTime createdAt;
}
