package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewQueryDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewStatusUpdateDTO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewDetailVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewListVO;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.AgentMessageReview;

public interface AgentMessageReviewService {
    AgentMessageReview recordAssistantTurn(LoginUser loginUser, String agentSessionId, AgentMessageReviewRecordDTO dto);

    AgentMessageReview submitFeedback(LoginUser loginUser, String agentSessionId, String messageId, AgentMessageReviewFeedbackDTO dto);

    PageResult<AgentMessageReviewListVO> pageReviews(LoginUser loginUser, AgentMessageReviewQueryDTO query);

    AgentMessageReviewDetailVO getReviewDetail(LoginUser loginUser, Long id);

    AgentMessageReviewDetailVO updateReviewStatus(LoginUser loginUser, Long id, AgentMessageReviewStatusUpdateDTO dto);
}
