package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class QualityStandardDTO extends BaseDTO {
    private String productType; // '黄冰糖' 或 '白冰糖'
    private String standardCode;
    private String standardLevel;
    private Integer version;
    private String status;
    private String standardName;
    private String remark;
    private List<QualityStandardItemDTO> items;

    @Override
    public Integer getId() {
        return null;
    }
}
