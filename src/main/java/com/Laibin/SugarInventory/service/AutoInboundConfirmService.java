package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.po.User;

public interface AutoInboundConfirmService {

    /**
     * 根据 batchId 和前端确认信息，分别调用半成品/成品入库服务完成真实入库。
     */
    void confirm(String batchId, AutoInboundConfirmRequest request, User user);
}

