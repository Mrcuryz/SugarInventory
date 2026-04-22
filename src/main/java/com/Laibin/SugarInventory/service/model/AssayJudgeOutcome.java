package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.vo.AssayAppliedStandardVO;
import com.Laibin.SugarInventory.domain.vo.AssayFailedMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardSnapshotVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssayJudgeOutcome {
    private String judgeResult;
    private String compatibleConclusion;
    private String judgeMessage;
    private Integer appliedStandardId;
    private String appliedStandardName;
    private Integer appliedStandardVersion;
    private Integer failedMetricCount;
    private String qualifiedStandardsJson;
    private String failedMetricsJson;
    private String standardSnapshotJson;
    private List<String> matchedStandards = new ArrayList<>();
    private List<AssayFailedMetricVO> failedMetrics = new ArrayList<>();
    private AssayAppliedStandardVO appliedStandard;
    private AssayStandardSnapshotVO standardSnapshot;
}
