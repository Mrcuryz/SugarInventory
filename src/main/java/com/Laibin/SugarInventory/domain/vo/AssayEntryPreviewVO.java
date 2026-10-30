package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class AssayEntryPreviewVO {
    boolean previewOnly;
    boolean confirmationAvailable;
    String productRef;
    LocalDateTime previewedAt;
    Integer productId;
    String productName;
    BigDecimal weightPerPiece;
    Integer piecesPerPallet;
    LocalDate sampleDate;
    Integer existingVersion;
    Integer proposedVersion;
    List<Metric> metrics;
    String judgementLabel;
    String judgementMessage;
    String appliedStandardName;
    Integer appliedStandardVersion;
    List<FailedMetric> failedMetrics;
    List<String> warnings;

    @Value
    @Builder
    public static class Metric {
        String field;
        String name;
        BigDecimal value;
        boolean provided;
        String unit;
    }

    @Value
    @Builder
    public static class FailedMetric {
        String name;
        BigDecimal value;
        String unit;
        String reason;
    }
}
