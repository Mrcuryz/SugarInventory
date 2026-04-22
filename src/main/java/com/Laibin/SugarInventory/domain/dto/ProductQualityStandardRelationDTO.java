package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductQualityStandardRelationDTO extends BaseDTO {
    private Integer productId;
    private Integer qualityStandardId;
    private Boolean isDefault;
    private Integer priority;
    private Boolean enabled;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String remark;

    @Override
    public Integer getId() {
        return null;
    }
}
