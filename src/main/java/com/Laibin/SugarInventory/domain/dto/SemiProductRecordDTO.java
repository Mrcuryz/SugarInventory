package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "半成品记录查询条件DTO")
public class SemiProductRecordDTO {
    @Schema(description = "产品名称，支持模糊查询", example = "中冰")
    private String productName;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "查询日期，格式yyyy-MM-dd", example = "2025-02-24")
    private LocalDate date;

    @Schema(description = "操作员姓名", example = "张三")
    private String operatorName;
}