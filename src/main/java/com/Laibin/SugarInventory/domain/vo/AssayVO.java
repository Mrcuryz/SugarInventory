package com.Laibin.SugarInventory.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
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

    @Schema(description = "产品ID", example = "1")
    private Integer productId;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @Schema(description = "采样日期", example = "2025-02-27")
    private LocalDate sampleDate;

    @Schema(description = "色值")
    private BigDecimal colorValue;

    @Schema(description = "还原糖分")
    private BigDecimal reducingSugar;

    @Schema(description = "干燥失重")
    private BigDecimal dryWeight;

    @Schema(description = "电导灰分")
    private BigDecimal conductivityAsh;

    @Schema(description = "蔗糖分")
    private BigDecimal sucrose;

    @Schema(description = "不溶于水杂质")
    private BigDecimal insolubleImpurity;

    @Schema(description = "pH值")
    private BigDecimal phValue;

    @Schema(description = "化验人名称", example = "李四")
    private String testerName;

    @Schema(description = "是否检验合格", example = "合格/不合格")
    private String isQualified;

    @Schema(description = "JSON格式的合格标准列表")
    private String qualifiedStandards;

    @Schema(description = "创建时间", example = "2025-02-24T10:15:30")
    private LocalDateTime createdAt;
}
