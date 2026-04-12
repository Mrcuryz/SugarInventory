package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PalletFlowDetailVO {
    private Long id;
    private Integer cycleNo;
    private Integer taskId;
    private String operationType;
    private String operationName;
    private LocalDateTime operationTime;
    private Integer operatorId;
    private String operatorName;
    private Integer productId;
    private String productName;
    private String productStatus;
    private Integer assayId;
    private Integer fromWarehouseId;
    private String fromWarehouseName;
    private String fromSide;
    private Integer fromRowNumber;
    private Integer fromLayer;
    private Integer toWarehouseId;
    private String toWarehouseName;
    private String toSide;
    private Integer toRowNumber;
    private Integer toLayer;
    private String remark;
    private String extData;
}
