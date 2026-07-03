package com.Laibin.SugarInventory.agent.service.impl;

import com.Laibin.SugarInventory.agent.service.AgentInterruptStateService;
import com.Laibin.SugarInventory.domain.po.AgentInterruptState;
import com.Laibin.SugarInventory.mapper.AgentInterruptStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AgentInterruptStateServiceImpl implements AgentInterruptStateService {
    private final AgentInterruptStateMapper interruptStateMapper;

    public AgentInterruptStateServiceImpl(AgentInterruptStateMapper interruptStateMapper) {
        this.interruptStateMapper = interruptStateMapper;
    }

    @Override
    public void recordCreated(String agentSessionId, Integer userId, String messageId, String interruptId,
                              String kind, LocalDateTime expiresAt) {
        if (isBlank(interruptId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentInterruptState existing = interruptStateMapper.selectById(interruptId);
        if (existing == null) {
            AgentInterruptState state = new AgentInterruptState();
            state.setInterruptId(limit(interruptId, 100));
            state.setAgentSessionId(limit(agentSessionId, 80));
            state.setUserId(userId);
            state.setMessageId(limit(messageId, 80));
            state.setKind(limit(kind, 40));
            state.setStatus("PENDING");
            state.setExpiresAt(expiresAt);
            state.setCreatedAt(now);
            state.setUpdatedAt(now);
            interruptStateMapper.insert(state);
            return;
        }
        existing.setStatus("PENDING");
        existing.setKind(limit(kind, 40));
        existing.setExpiresAt(expiresAt);
        existing.setUpdatedAt(now);
        interruptStateMapper.updateById(existing);
    }

    @Override
    public void recordResumeRequested(String agentSessionId, Integer userId, String interruptId, String action,
                                      String optionId, String previewId, String clientRequestId) {
        if (isBlank(interruptId)) {
            return;
        }
        AgentInterruptState state = interruptStateMapper.selectById(interruptId);
        if (state == null) {
            state = new AgentInterruptState();
            state.setInterruptId(limit(interruptId, 100));
            state.setAgentSessionId(limit(agentSessionId, 80));
            state.setUserId(userId);
            state.setStatus("PENDING");
            state.setCreatedAt(LocalDateTime.now());
        }
        state.setResumeAction(limit(action, 40));
        state.setOptionId(limit(optionId, 100));
        state.setPreviewId(limit(previewId, 100));
        state.setClientRequestId(limit(clientRequestId, 100));
        state.setUpdatedAt(LocalDateTime.now());
        if (state.getAgentSessionId() == null) {
            state.setAgentSessionId(limit(agentSessionId, 80));
        }
        if (state.getUserId() == null) {
            state.setUserId(userId);
        }
        if (state.getCreatedAt() == null) {
            state.setCreatedAt(LocalDateTime.now());
        }
        if (interruptStateMapper.selectById(interruptId) == null) {
            interruptStateMapper.insert(state);
        } else {
            interruptStateMapper.updateById(state);
        }
    }

    @Override
    public void recordTerminal(String agentSessionId, Integer userId, String interruptId, String status,
                               String resultCode, String errorCode) {
        if (isBlank(interruptId)) {
            return;
        }
        AgentInterruptState state = interruptStateMapper.selectById(interruptId);
        if (state == null) {
            state = new AgentInterruptState();
            state.setInterruptId(limit(interruptId, 100));
            state.setAgentSessionId(limit(agentSessionId, 80));
            state.setUserId(userId);
            state.setCreatedAt(LocalDateTime.now());
        }
        LocalDateTime now = LocalDateTime.now();
        state.setStatus(limit(status, 40));
        state.setResultCode(limit(resultCode, 40));
        state.setErrorCode(limit(errorCode, 80));
        state.setResumedAt(now);
        state.setUpdatedAt(now);
        if (interruptStateMapper.selectById(interruptId) == null) {
            interruptStateMapper.insert(state);
        } else {
            interruptStateMapper.updateById(state);
        }
    }

    @Override
    public void cancelSessionInterrupts(String agentSessionId) {
        cancelSessionInterrupts(agentSessionId, "SESSION_CLOSED_OR_REPLACED");
    }

    @Override
    public void cancelSessionInterrupts(String agentSessionId, String resultCode) {
        if (isBlank(agentSessionId)) {
            return;
        }
        AgentInterruptState update = new AgentInterruptState();
        update.setStatus("CANCELLED");
        update.setResultCode(limit(resultCode, 40));
        update.setUpdatedAt(LocalDateTime.now());
        interruptStateMapper.update(update, new LambdaQueryWrapper<AgentInterruptState>()
                .eq(AgentInterruptState::getAgentSessionId, agentSessionId)
                .eq(AgentInterruptState::getStatus, "PENDING"));
    }

    @Override
    public String findOwnedStatus(String agentSessionId, Integer userId, String interruptId) {
        if (isBlank(agentSessionId) || isBlank(interruptId)) {
            return null;
        }
        AgentInterruptState state = interruptStateMapper.selectById(interruptId);
        if (state == null || !agentSessionId.equals(state.getAgentSessionId())) {
            return null;
        }
        if (userId != null && state.getUserId() != null && !userId.equals(state.getUserId())) {
            return null;
        }
        return state.getStatus();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
