package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

@Data
public class AutoInboundBatchOptionVO {
    private String batchId;
    private String parseTime;
    private String displayName;
    private Integer taskCount;
    private String status;
    private String parseType;
}
