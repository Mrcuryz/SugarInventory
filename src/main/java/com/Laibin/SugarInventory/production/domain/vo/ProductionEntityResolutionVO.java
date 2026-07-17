package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProductionEntityResolutionVO {
    String resolutionStatus;
    boolean needsUserSelection;
    String entityType;
    String query;
    List<ProductionEntityCandidateVO> candidates;
    List<String> limitations;
}
