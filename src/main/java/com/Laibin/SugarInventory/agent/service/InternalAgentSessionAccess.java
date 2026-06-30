package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.domain.po.AgentSession;

public record InternalAgentSessionAccess(AgentSession session, LoginUser loginUser) {
}
