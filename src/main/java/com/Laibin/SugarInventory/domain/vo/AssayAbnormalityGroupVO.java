package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AssayAbnormalityGroupVO {
    private String groupLabel;
    private long total;
    private long failedCount;
    private long noStandardCount;
    private long multipleCandidatesCount;
    private LocalDate latestSampleDate;
    private List<String> riskLabels;
}
