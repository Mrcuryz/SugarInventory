package com.Laibin.SugarInventory.domain.vo;

import com.Laibin.SugarInventory.domain.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "出库记录")
public class OutStockRecordVO {
    private String warehouseName;

    private String productName;

    private Integer quantity;

    private LocalDate inDate;

    private BigDecimal totalWeight;

    private LocalDate outDate;

    private String operator;

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

    private LocalDateTime createdAt;
}
