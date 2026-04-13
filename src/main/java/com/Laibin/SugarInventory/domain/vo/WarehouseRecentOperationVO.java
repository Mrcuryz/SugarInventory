package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "库位最近操作记录")
public class WarehouseRecentOperationVO {
    @Schema(description = "操作时间")
    private LocalDateTime operationTime;
    @Schema(description = "操作类型")
    private String operationType;
    @Schema(description = "操作名称")
    private String operationName;
    @Schema(description = "操作人")
    private String operatorName;
    @Schema(description = "托盘码")
    private String palletCode;
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "原库位")
    private String fromWarehouseName;
    @Schema(description = "目标库位")
    private String toWarehouseName;
    @Schema(description = "摘要说明")
    private String remark;
}
