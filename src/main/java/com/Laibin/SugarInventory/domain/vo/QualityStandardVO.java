package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class QualityStandardVO extends BaseVO {
    private Integer id;
    private String standardCode;
    private String standardName;
    private String productType;
    private String standardLevel;
    private Integer version;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer createdBy;
    private Integer updatedBy;
    private List<QualityStandardItemVO> items;
    private List<QualityStandardRelatedProductVO> relatedProducts;
}
