package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ProductionReportOutputRowVO {
    private Long productionOrderId;
    private Integer productId;
    private String productName;
    private String productStatus;
    private LocalDate productionDate;
    private Integer boardCount;
    private Integer pieceCount;
    private Integer totalPieces;
    private BigDecimal totalWeight;
    private Integer requiredQrCount;
    private Integer boundQrCount;
    private Integer inboundQrCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
