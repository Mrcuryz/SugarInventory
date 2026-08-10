package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionControlCodec;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionAudit;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionAuditService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final AgentFinishInboundExecutionAuditMapper auditMapper;

    public void append(String confirmationRef, String executionRef, Integer ownerUserId,
                       String agentSessionId, String eventType, String fromStatus, String toStatus,
                       String resultCode, String errorCode, String safeDetails) {
        AgentFinishInboundExecutionAudit audit = new AgentFinishInboundExecutionAudit();
        audit.setConfirmationRef(confirmationRef);
        audit.setExecutionRef(executionRef);
        audit.setOwnerUserId(ownerUserId);
        audit.setAgentSessionId(agentSessionId);
        audit.setEventType(eventType);
        audit.setFromStatus(fromStatus);
        audit.setToStatus(toStatus);
        audit.setResultCode(resultCode);
        audit.setErrorCode(errorCode);
        audit.setDetailsSha256(FinishInboundExecutionControlCodec.sha256(
                safeDetails == null ? eventType : safeDetails));
        audit.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE));
        auditMapper.insert(audit);
    }
}
