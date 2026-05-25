package com.Laibin.SugarInventory.production.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductionMaterialPickDTO {
    private List<Integer> palletCodeIds;
    private List<String> palletCodes;
    private String remark;
}
