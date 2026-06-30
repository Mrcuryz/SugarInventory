package com.Laibin.SugarInventory.agent.internal.service;

import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolRequestDTO;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolResponseVO;

public interface InternalAgentToolGatewayService {
    InternalAgentToolResponseVO invoke(String serviceKey, String toolName, InternalAgentToolRequestDTO request);
}
