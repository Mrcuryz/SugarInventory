package com.Laibin.SugarInventory.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class AssayRecordsVO {
    String scopeLabel;
    String dateRangeLabel;
    long total;
    long passCount;
    long failedCount;
    long noStandardCount;
    long multipleCandidatesCount;
    LocalDate latestSampleDate;
    String summaryText;
    int page;
    int size;
    List<AssayRecordRowVO> records;
    List<String> notes;
}
