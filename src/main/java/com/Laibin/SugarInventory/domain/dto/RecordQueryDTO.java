package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "半成品记录查询条件DTO")
public class RecordQueryDTO {
    @DateTimeFormat(pattern = "yyyyMMdd")
    @Schema(description = "操作日期，格式为yyyyMMdd，非必填", example = "20250224")
    private LocalDate operationDate;
}