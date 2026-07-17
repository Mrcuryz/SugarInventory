package com.Laibin.SugarInventory.mapper.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AssayAbnormalitySummaryRow {
    private Long total;
    private Long failedCount;
    private Long noStandardCount;
    private Long multipleCandidatesCount;
    private LocalDate latestSampleDate;
}
