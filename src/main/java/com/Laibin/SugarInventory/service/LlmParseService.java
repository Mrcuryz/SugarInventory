package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.po.LlmParseResult;

public interface LlmParseService {
    /**
     * 调用大模型解析微信报数文本，输出统一结构 LlmParseResult
     */
    LlmParseResult parseReport(AutoInboundParseRequest request);
}
