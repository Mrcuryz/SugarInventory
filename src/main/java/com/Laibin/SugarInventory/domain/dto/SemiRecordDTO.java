package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "入库产品关联的半成品信息")
@Data
public class SemiRecordDTO {
    @Schema(description = "半成品ID", example = "1")
    private Integer semiProductId;

    @Schema(description = "产品名称")
    private String productName;

    @Schema(description = "生产日期", example = "2025-01-01")
    private LocalDate productionDate;

    @Schema(description = "库位", example = "1")
    private Integer warehouseId;

    @Schema(description = "使用的半成品重量", example = "20")
    private Integer quantity;

    @NotNull(message = "单位不能为空")
    @Schema(description = "单位0板1件（板/件）", example = "30")
    private String unit;

    @Schema(description = "是否套用该半成品的化验数据", example = "false")
    private Boolean useAssay = false;
}
