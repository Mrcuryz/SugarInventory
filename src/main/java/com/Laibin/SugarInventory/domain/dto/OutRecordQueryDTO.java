package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@Schema(description = "查询出库记录DTO")
public class OutRecordQueryDTO {
    private String warehouseName;

    private String productName;

    @Schema(description = "查询起始日期", example = "2025-01-01")
    private Date startDate;

    @Schema(description = "查询结束日期", example = "2025-03-31")
    private Date endDate;

    private Integer operatorName;

    @Schema(description = "当前页码", example = "1")
    private Integer page;

    @Schema(description = "每页记录数", example = "10")
    private Integer size;
}
