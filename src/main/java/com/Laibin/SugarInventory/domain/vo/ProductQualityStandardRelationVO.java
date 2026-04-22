package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProductQualityStandardRelationVO {
    private Integer id;
    private Integer productId;
    private String productName;
    private Integer qualityStandardId;
    private String standardCode;
    private String standardName;
    private Integer standardVersion;
    private String standardStatus;
    private Boolean isDefault;
    private Integer priority;
    private Boolean enabled;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
