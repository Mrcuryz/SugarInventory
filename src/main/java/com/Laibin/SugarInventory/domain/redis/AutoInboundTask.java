package com.Laibin.SugarInventory.domain.redis;

import com.Laibin.SugarInventory.domain.dto.SemiRecordDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundRiskLevel;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class AutoInboundTask {

    private String taskId;
    private String batchId;

    /**
     * 半成品 / 成品
     */
    private AutoInboundType type;

    /**
     * 风险等级 + 原因说明
     */
    private AutoInboundRiskLevel riskLevel;
    private String riskReason;

    /**
     * 原始文本块 + LLM 备注
     */
    private String rawBlock;
    private String remark;          // 把 ParsedInboundItem.remark + source.remark 等汇总

    /**
     * 通用字段
     */
    private LocalDate entryDate;
    private String side = "左";     // 默认左
    private Boolean hasAssay;       // 是否有当天化验记录（半成品/成品）

    /**
     * ==== 半成品入库：目标产品 & 仓位 & 数量 ====
     */
    private Integer semiProductId;
    private String semiProductName;
    private Integer semiWarehouseId;
    private String semiWarehouseName;
    private Integer semiBoardQuantity;
    private Integer semiPieceQuantity;

    /**
     * ==== 成品入库：目标产品 & 仓位 & 数量 ====
     */
    private Integer productId;
    private String productName;
    private Integer warehouseId;
    private String warehouseName;
    private Integer finishedBoardQuantity;
    private Integer finishedPieceQuantity;

    /**
     * 标准化后的单二维码任务项。
     */
    private Integer requiredQrCount;
    private Integer availableQrCount;
    private List<String> missingFields;
    private List<String> warnings;
    private List<AutoInboundTaskItem> taskItems;
    private String status;

    /**
     * 成品入库时建议的半成品记录（映射自 sources，可被前端编辑后作为 dto.semiRecords 传回）
     */
    private List<SemiRecordDTO> suggestedSemiRecords;

    private List<SemiRecordDTO> semiRecords;

    /**
     * 是否允许自动入库（绿/黄 true，红 false）
     */
    private boolean canAutoStockIn;
}

