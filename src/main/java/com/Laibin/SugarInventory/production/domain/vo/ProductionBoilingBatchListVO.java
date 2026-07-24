package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProductionBoilingBatchListVO {
    String dataScope;
    String scopeLabel;
    String dateRangeLabel;
    long total;
    List<ProductionEntityCandidateVO> candidates;
    List<String> limitations;
}
