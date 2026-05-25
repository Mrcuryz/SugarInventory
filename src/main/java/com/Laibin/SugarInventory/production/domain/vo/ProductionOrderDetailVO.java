package com.Laibin.SugarInventory.production.domain.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductionOrderDetailVO {
    private ProductionOrderBaseVO baseInfo;
    private List<ProductionMaterialVO> materials;
    private List<ProductionOutputVO> outputs;
    private List<ProductionOutputCodeVO> outputCodes;
    private List<ProductionLabelBatchVO> labelBatches;
}
