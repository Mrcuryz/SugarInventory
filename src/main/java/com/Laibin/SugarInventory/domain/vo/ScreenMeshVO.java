package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class ScreenMeshVO extends BaseVO {
    private Integer id;
    private String meshName;
    private String description;
    private LocalDateTime createdAt;
    private Integer createdBy;
    private LocalDateTime updatedAt;
    private Integer updatedBy;
}
