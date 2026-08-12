package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 智能报数入库确认时允许用户修订的字段白名单。
 * 批次、任务类型、状态、二维码和执行结果均不从客户端接收。
 */
@Data
public class AutoInboundTaskUpdateDTO {
    private String taskId;
    private LocalDate entryDate;
    private String side;
    private Integer semiProductId;
    private Integer semiWarehouseId;
    private String semiWarehouseName;
    private Integer semiBoardQuantity;
    private Integer semiPieceQuantity;
    private Integer productId;
    private Integer warehouseId;
    private String warehouseName;
    private Integer finishedBoardQuantity;
    private Integer finishedPieceQuantity;
    private String remark;
}
