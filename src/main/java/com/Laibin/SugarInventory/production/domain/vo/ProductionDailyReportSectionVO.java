package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionDailyReportSectionVO {
    private String departmentCode;
    private String departmentName;
    private String status;
    private Integer version;
    private String updatedByName;
    private LocalDateTime updatedAt;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private List<ProductionDailyReportGroupVO> groups = new ArrayList<>();
}
