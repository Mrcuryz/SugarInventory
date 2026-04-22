package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class AssayStandardSnapshotVO {
    private Integer id;
    private String standardCode;
    private String standardName;
    private String productType;
    private String standardLevel;
    private Integer version;
    private String status;
    private String remark;
    private List<QualityStandardItemVO> items;
}
