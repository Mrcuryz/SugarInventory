package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "化验记录返回 VO")
public class AssayVO {
    @Schema(description = "化验记录ID", example = "1")
    private Integer id;

    @Schema(description = "产品ID", example = "1")
    private Integer productId;

    @Schema(description = "产品名称", example = "一级黄冰糖")
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

    @Schema(description = "pH")
    private BigDecimal phValue;

    @Schema(description = "化验员姓名", example = "李四")
    private String testerName;

    @Schema(description = "兼容旧前端的结论", example = "合格/不合格/无标准")
    private String isQualified;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "匹配标准名称 JSON")
    private String qualifiedStandards;

    @Schema(description = "采用标准ID")
    private Integer appliedStandardId;

    @Schema(description = "采用标准名称")
    private String appliedStandardName;

    @Schema(description = "采用标准版本")
    private Integer appliedStandardVersion;

    @Schema(description = "判定结果")
    private String judgeResult;

    @Schema(description = "判定说明")
    private String judgeMessage;

    @Schema(description = "不达标指标数量")
    private Integer failedMetricCount;

    @Schema(description = "不达标指标 JSON")
    private String failedMetricsJson;

    @Schema(description = "标准快照 JSON")
    private String standardSnapshotJson;

    @Schema(description = "创建时间", example = "2025-02-24T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "匹配到的标准名称列表")
    private List<String> matchedStandards;

    @Schema(description = "采用标准")
    private AssayAppliedStandardVO appliedStandard;

    @Schema(description = "不达标指标明细")
    private List<AssayFailedMetricVO> failedMetrics;

    @Schema(description = "标准快照")
    private AssayStandardSnapshotVO standardSnapshot;
}
