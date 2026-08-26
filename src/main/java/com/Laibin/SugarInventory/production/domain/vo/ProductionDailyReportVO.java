package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductionDailyReportVO {
    private Long id;
    private LocalDate reportDate;
    private LocalDate preparedDate;
    private String preparedByName;
    private String status;
    private Integer version;
    private String updatedByName;
    private LocalDateTime updatedAt;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private List<ProductionDailyReportSectionVO> sections = new ArrayList<>();
}
