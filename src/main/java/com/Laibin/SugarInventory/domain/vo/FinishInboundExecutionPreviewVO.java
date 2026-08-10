package com.Laibin.SugarInventory.domain.vo;

import com.Laibin.SugarInventory.agent.model.FinishInboundExecutionPreviewSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class FinishInboundExecutionPreviewVO {
    private String dataScope;
    private int previewVersion;
    private String previewStatus;
    private String previewRef;
    private String stateDigest;
    private LocalDateTime previewedAt;
    private LocalDateTime expiresAt;
    private boolean readyForUserConfirmation;
    private int requestedItemCount;
    private int eligibleItemCount;
    private List<Item> items;
    private List<String> blockingIssues;
    private List<String> warnings;
    private List<String> limitations;

    @JsonIgnore
    private FinishInboundExecutionPreviewSnapshot serverSnapshot;

    @Getter
    @Builder
    public static class Item {
        private String palletCode;
        private String productName;
        private LocalDate productionDate;
        private String warehouseName;
        private LocalDate entryDate;
        private String side;
        private Integer quantity;
        private String unitLabel;
        private String remark;
        private boolean quantityLockedByProductionOutput;
    }
}
