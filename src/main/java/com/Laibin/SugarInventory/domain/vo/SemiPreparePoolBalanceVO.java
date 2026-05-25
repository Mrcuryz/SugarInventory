package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SemiPreparePoolBalanceVO {
    private Long id;
    private Integer productId;
    private String productName;
    private LocalDate productionDate;
    private Integer screenMeshId;
    private String screenMeshName;
    private Integer assayId;
    private Integer inPieces;
    private Integer consumedPieces;
    private Integer remainingPieces;
    private Integer piecesPerPallet;
    private BigDecimal weightPerPiece;
    private BigDecimal remainingWeight;
    private String status;
    private LocalDateTime firstInAt;
    private LocalDateTime lastInAt;
    private LocalDateTime lastConsumedAt;
}
