package com.Laibin.SugarInventory.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auto_inbound_execution")
public class AutoInboundExecution {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("batch_id")
    private String batchId;

    @TableField("source_task_id")
    private String sourceTaskId;

    @TableField("request_hash")
    private String requestHash;

    @TableField("status")
    private String status;

    @TableField("result_json")
    private String resultJson;

    @TableField("created_by")
    private Integer createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
