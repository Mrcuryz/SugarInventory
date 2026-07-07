package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.domain.po.AgentMessageReview;

public interface AgentMessageReviewService {
    AgentMessageReview recordAssistantTurn(LoginUser loginUser, String agentSessionId, AgentMessageReviewRecordDTO dto);

    AgentMessageReview submitFeedback(LoginUser loginUser, String agentSessionId, String messageId, AgentMessageReviewFeedbackDTO dto);
}
