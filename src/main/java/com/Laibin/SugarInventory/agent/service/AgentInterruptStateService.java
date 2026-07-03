package com.Laibin.SugarInventory.agent.service;

import java.time.LocalDateTime;

public interface AgentInterruptStateService {
    void recordCreated(String agentSessionId, Integer userId, String messageId, String interruptId,
                       String kind, LocalDateTime expiresAt);

    void recordResumeRequested(String agentSessionId, Integer userId, String interruptId, String action,
                               String optionId, String previewId, String clientRequestId);

    void recordTerminal(String agentSessionId, Integer userId, String interruptId, String status,
                        String resultCode, String errorCode);

    void cancelSessionInterrupts(String agentSessionId);

    void cancelSessionInterrupts(String agentSessionId, String resultCode);

    String findOwnedStatus(String agentSessionId, Integer userId, String interruptId);
}
