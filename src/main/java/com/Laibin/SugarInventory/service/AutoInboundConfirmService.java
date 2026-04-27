package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;

public interface AutoInboundConfirmService {

    /**
     * 根据 batchId 和前端确认信息，分配固定产品二维码并走二维码入库链路。
     */
    AutoInboundParseResponse confirm(String batchId, AutoInboundConfirmRequest request, User user);
}

