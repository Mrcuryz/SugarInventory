package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Schema(description = "单产品化验录入只读预览；不创建化验记录或执行凭据")
public class AssayEntryPreviewDTO {
    @NotNull @Positive
    @Schema(description = "已由用户明确选择的具体产品ID，不支持产品大类或化验组")
    private Integer productId;
    @NotNull
    @Schema(description = "采样日期，沿用现有化验录入表单含义，不自动用当前日期补齐")
    private LocalDate sampleDate;
    private BigDecimal colorValue;
    private BigDecimal reducingSugar;
    private BigDecimal dryWeight;
    private BigDecimal conductivityAsh;
    private BigDecimal sucrose;
    private BigDecimal insolubleImpurity;
    private BigDecimal phValue;

    @JsonIgnore
    @Schema(hidden = true)
    private final Set<String> unsupportedFields = new LinkedHashSet<>();

    @JsonAnySetter
    public void recordUnsupportedField(String name, Object ignoredValue) {
        unsupportedFields.add(name);
    }
}
