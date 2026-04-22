package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

@Data
public class AssayAppliedStandardVO {
    private Integer id;
    private String standardCode;
    private String standardName;
    private String productType;
    private String standardLevel;
    private Integer version;
    private String status;
    private String remark;
}
