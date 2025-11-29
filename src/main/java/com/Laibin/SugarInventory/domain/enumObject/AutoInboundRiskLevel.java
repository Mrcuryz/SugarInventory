package com.Laibin.SugarInventory.domain.enumObject;

public enum AutoInboundRiskLevel {
    GREEN,  // 信息完整，硬约束全部满足
    YELLOW, // 可入库，但有不确定/疑点
    RED     // 缺关键信息，必须人工补
}
