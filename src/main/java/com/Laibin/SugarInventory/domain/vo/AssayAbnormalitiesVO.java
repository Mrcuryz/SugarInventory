package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class AssayAbnormalitiesVO {
    private String scopeLabel;
    private String dateRangeLabel;
    private String groupBy;
    private long total;
    private long failedCount;
    private long noStandardCount;
    private long multipleCandidatesCount;
    private LocalDate latestSampleDate;
    private String summaryText;
    private List<AssayAbnormalityGroupVO> groups;
    private List<String> riskLabels;
    private List<String> notes;
}
