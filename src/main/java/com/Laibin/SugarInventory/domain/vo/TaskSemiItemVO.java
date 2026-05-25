package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
/**
 * 返回给前端的成品任务半成品明细视图。
 */
public class TaskSemiItemVO {
    private Integer id;
    private String semiPalletCode;
    private Long prepareBalanceId;
    private Integer semiProductId;
    private String semiProductName;
    private LocalDate productionDate;
    private Integer quantity;
    private String unit;
    private Integer boardCount;
    private Integer pieceCount;
    private Integer totalPieces;
    private Integer remainingPieces;
    private Boolean useAssay;
}
