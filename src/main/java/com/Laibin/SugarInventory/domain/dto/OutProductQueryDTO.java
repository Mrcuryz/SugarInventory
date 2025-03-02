package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "查询符合条件的出库产品DTO")
public class OutProductQueryDTO {
    @Schema(description = "产品名称")
    private String productName;
    @Schema(description = "生产日期 起始")
    private LocalDate startDate;
    @Schema(description = "生产日期 终止")
    private LocalDate endDate;
    @Schema(description = "色值 最小值")
    private BigDecimal colorValueMin;
    @Schema(description = "色值 最大值")
    private BigDecimal colorValueMax;
    @Schema(description = "还原糖含量 最小值")
    private BigDecimal reducingSugarMin;
    @Schema(description = "还原糖含量 最大值")
    private BigDecimal reducingSugarMax;
    @Schema(description = "pH 最小值")
    private BigDecimal phMin;
    @Schema(description = "pH 最大值")
    private BigDecimal phMax;
    @Schema(description = "筛网Id")
    private Integer ScreenMeshId;
}