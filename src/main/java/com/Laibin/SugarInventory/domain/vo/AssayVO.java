package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "化验记录返回VO")
public class AssayVO {
    @Schema(description = "化验记录ID", example = "1")
    private Integer id;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @Schema(description = "采样日期", example = "2025-02-27")
    private LocalDate sampleDate;

    @Schema(description = "色值", example = "85.5")
    private BigDecimal colorValue;

    @Schema(description = "还原糖含量", example = "20.0")
    private BigDecimal reducingSugar;

    @Schema(description = "pH值", example = "6.2")
    private BigDecimal phValue;

    @Schema(description = "化验人名称", example = "李四")
    private String testerName;

    @Schema(description = "创建时间", example = "2025-02-24T10:15:30")
    private LocalDateTime createdAt;
}
