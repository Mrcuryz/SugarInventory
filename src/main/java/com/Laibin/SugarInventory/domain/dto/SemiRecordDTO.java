package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "入库产品关联的半成品信息")
@Data
public class SemiRecordDTO {
    @Schema(description = "半成品ID", example = "1")
    private Integer semiProductId;
    @Schema(description = "生产日期", example = "2025-01-01")
    private LocalDate productionDate;
    @Schema(description = "使用的半成品数量", example = "20")
    private Integer quantity;
}
