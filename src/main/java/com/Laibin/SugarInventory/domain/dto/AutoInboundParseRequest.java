package com.Laibin.SugarInventory.domain.dto;

import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.User;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AutoInboundParseRequest {
    /** 微信报数文本 */
    private String rawText;

    /** 入库日期（报数日期） */
    private LocalDate entryDate;

    /** 操作人（当前登录用户） */
    private User operator;

    /** 由前端选择或自动判断：SEMI_PRODUCT / FINISHED_PRODUCT / MIXED */
    private AutoInboundType parseType;
}