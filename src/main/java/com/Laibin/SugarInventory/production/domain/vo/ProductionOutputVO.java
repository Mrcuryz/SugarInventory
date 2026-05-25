package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductionOutputVO {
    private Long id;
    private Long productionOrderId;
    private String orderNo;
    private Integer productId;
    private String productName;
    private String productStatus;
    private LocalDate productionDate;
    private Integer boardCount;
    private Integer pieceCount;
    private Integer totalPieces;
    private Integer piecesPerPallet;
    private BigDecimal totalWeight;
    private Integer requiredQrCount;
    private Integer boundQrCount;
    private Integer inboundQrCount;
    private String status;
    private LocalDateTime createdAt;
    private String remark;
    private List<ProductionOutputCodeVO> codes;
}
