package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class TaskTransitionPreviewVO {
    String dataScope;
    int previewVersion;
    String previewStatus;
    String previewRef;
    String stateDigest;
    LocalDateTime previewedAt;
    LocalDateTime expiresAt;
    String transition;
    String transitionLabel;
    boolean canOpenBusinessDialog;
    int requestedTaskCount;
    int eligibleTaskCount;
    List<Task> tasks;
    List<String> requiredUserInputs;
    List<String> blockingIssues;
    List<String> warnings;
    List<String> limitations;

    @Value
    @Builder
    public static class Task {
        String palletCode;
        String currentTaskStatus;
        String productName;
        String productType;
        LocalDate productionDate;
        BigDecimal totalWeight;
        String presetWarehouseName;
        String presetSide;
        boolean quantityLockedByProductionOutput;
    }
}
