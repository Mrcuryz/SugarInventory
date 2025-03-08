package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "符合条件的产品VO")
public class OutProductVO {
    @Schema(description = "产品ID")
    private String productName;
    @Schema(description = "检测日期（入库日期）")
    private LocalDate sampleDate; // 对应检测日期
    @Schema(description = "色值")
    private BigDecimal colorValue; // 类型与数据库一致
    @Schema(description = "还原糖含量")
    private BigDecimal reducingSugar;
    @Schema(description = "pH值")
    private BigDecimal phValue;
    @Schema(description = "筛网名称")
    private String meshName;
    @Schema(description = "库位左/右侧")
    private String side;
    @Schema(description = "排数")
    private Integer rowNumber;
    @Schema(description = "层数")
    private Integer layer;
    @Schema(description = "数量")
    private Integer quantity;
}