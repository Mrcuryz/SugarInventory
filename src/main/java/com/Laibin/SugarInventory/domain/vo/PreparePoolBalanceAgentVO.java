package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class PreparePoolBalanceAgentVO {
    private String dataScope;
    private long total;
    private int page;
    private int size;
    private LocalDateTime balanceAsOf;
    private List<Row> records;
    private List<String> limitations;

    @Data @Builder
    public static class Row {
        private String productName;
        private LocalDate productionDate;
        private String screenMeshName;
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
}
