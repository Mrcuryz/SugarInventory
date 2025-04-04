package com.Laibin.SugarInventory.domain.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@Schema(description = "入库记录返回VO")
public class InStockVO {
    @Schema(description = "入库记录ID", example = "1")
    private Integer id;

    @Schema(description = "产品名称", example = "中冰")
    private String productName;

    @Schema(description = "库位名称", example = "101")
    private String warehouseName;

    @Schema(description = "数量", example = "250")
    private Integer quantity;

    @Schema(description = "总重量（kg）", example = "3750.00")
    private BigDecimal totalWeight;

    @Schema(description = "入库日期", example = "2025-02-25")
    private LocalDate entryDate;

    @Schema(description = "操作员名称", example = "张三")
    private String operator;

    @Schema(description = "筛网规格名称", example = "大筛网")
    private String meshName;

    @Schema(description = "半成品记录", example = "5")
    private String semiProductRecords;

    @Schema(description = "化验记录id")
    private Integer assayId;

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

    private Integer testedBy;

    @Schema(description = "化验人名称", example = "李四")
    private String testerName;

    @Schema(description = "是否检验合格", example = "合格/不合格")
    private String isQualified;

    @Schema(description = "JSON格式的合格标准")
    private String qualifiedStandards;

    @Schema(description = "创建时间", example = "2025-02-25T16:00:00.000+00:00")
    private LocalDateTime createdAt;
}