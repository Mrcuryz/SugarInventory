package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder
public class FixedProductQrPoolAgentVO {
    private String dataScope;
    private long total;
    private int page;
    private int size;
    private LocalDateTime poolAsOf;
    private List<Row> records;
    private List<String> limitations;

    @Data @Builder
    public static class Row {
        private String code;
        private String fixedProductName;
        private String status;
        private Boolean fixedModeEnabled;
        private Boolean allowPrint;
        private LocalDateTime updatedAt;
    }
}
