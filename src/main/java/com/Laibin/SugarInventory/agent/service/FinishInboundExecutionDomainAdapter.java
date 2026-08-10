package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.User;

import java.util.List;

public interface FinishInboundExecutionDomainAdapter {
    AdapterResult execute(AgentFinishInboundExecutionPreview preview, User user);

    record AdapterResult(String resultCode, int affectedPalletCount, List<String> palletCodes) {
    }
}
