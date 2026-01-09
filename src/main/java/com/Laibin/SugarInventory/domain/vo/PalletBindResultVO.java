package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
/**
 * 绑定托盘并创建任务后的返回结果，便于前端展示任务/托盘/产品信息。
 */
public class PalletBindResultVO {
    private Integer palletCodeId;
    private String code;
    private String palletStatus;
    private Integer taskId;
    private String taskType;
    private String taskStatus;
    private Integer productId;
    private String productName;
    private String productType;
    private String productStatus;
    private Integer screenMeshId;
    private String screenMeshName;
    private LocalDate productionDate;
    private LocalDateTime createdAt;
    private String remark;
}
