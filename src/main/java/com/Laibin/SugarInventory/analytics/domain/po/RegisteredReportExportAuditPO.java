package com.Laibin.SugarInventory.analytics.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_report_export_audit")
public class RegisteredReportExportAuditPO {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String auditRef;
    private String reportRunId;
    private Integer exporterUserId;
    private String exporterDisplayName;
    private String exportFormat;
    private String contentSha256;
    private LocalDateTime exportedAt;
}
