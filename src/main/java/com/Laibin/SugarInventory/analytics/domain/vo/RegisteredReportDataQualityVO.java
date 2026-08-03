package com.Laibin.SugarInventory.analytics.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RegisteredReportDataQualityVO {
    boolean partial;
    int rowsMissingWeight;
    int rowsMissingPieceConversion;
    int rowsMissingProductName;
    Integer rowsMissingJudgeResult;
    Integer rowsMissingStandardVersion;
    Integer lateRecordedCount;
    Integer rowsMissingMetricValue;
    Integer rowsWithoutComparableMetricStandard;
    Integer rowsWithUnexpectedMetricUnit;
    Integer completedOrderCount;
    Integer completedOrdersWithInputCount;
    Integer completedOrdersMissingInputCount;
    Integer completedOrdersWithStableOutputCount;
    Integer completedOrdersMissingOutputCount;
    Integer draftOutputExcludedCount;
    Integer canceledOutputExcludedCount;
    Integer crossDayInboundCount;
    Integer unattributedOrderCount;
    Boolean orderBreakdownTruncated;
    Integer invalidDurationCount;
    Integer canceledWithoutTerminalTimeCount;
    Integer tasksWithoutFlowRecordCount;
    Integer tasksWithoutOperationBatchCount;
    Boolean simulationData;
    Integer replayMovementRecordCount;
    Boolean replayAnchorReconciled;
    Integer trustedSnapshotDayCount;
    Integer requiredSnapshotDayCount;
    List<String> notes;
}
