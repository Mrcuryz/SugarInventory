package com.Laibin.SugarInventory.domain.po;

import lombok.Data;

@Data
public class ParsedSemiSource {

    /** RAW_MATERIAL / SEMI_PRODUCT */
    private String sourceType;

    /** 报数里的品名原文，如“直破黄小颗粒”“40Kg黄碎冰” */
    private String productNameRaw;

    /** 生产日期（如 2025-10-18），缺失则 null 或 "" */
    private String productionDate;

    /** 板数 / 件数（允许一个为 0） */
    private Integer boardCount;
    private Integer pieceCount;

    /** 仓位提示，如“柳冰”“来冰”“1号库位”等，用于辅助匹配半成品仓库 */
    private String warehouseHint;

    /** 批号 / 其他附属信息，如“无号”*/
    private String batchNo;

    /** LLM 自己加的备注（不并入硬字段） */
    private String remark;
}
