package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class ProductionEntityCandidateVO {
    String entityRef;
    String entityType;
    String displayCode;
    String status;
    LocalDate businessDate;
    String summary;
}
