package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.impl.AgentMessageReviewServiceImpl;
import com.Laibin.SugarInventory.domain.po.AgentMessageReview;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewEvidenceMapper;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMessageReviewServiceImplTest {
    private AgentMessageReviewMapper reviewMapper;
    private AgentMessageReviewServiceImpl service;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(AgentMessageReviewMapper.class);
        AgentMessageReviewEvidenceMapper evidenceMapper = mock(AgentMessageReviewEvidenceMapper.class);
        AgentSessionService agentSessionService = mock(AgentSessionService.class);
        service = new AgentMessageReviewServiceImpl(reviewMapper, evidenceMapper, agentSessionService);

        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));
    }

    @Test
    void recordAssistantTurnMarksBusinessAnswerWithoutToolAsLowConfidence() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-1");
        dto.setUserQuestion("8号库位的库存情况");
        dto.setAssistantAnswerTextSafe("未找到匹配库位，请换一个库位名称。");
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("LOW_CONFIDENCE");
        assertThat(review.getConfidenceLevel()).isEqualTo("LOW");
        assertThat(review.getFailureDomain()).isEqualTo("DATA");
        assertThat(review.getFailureCategory()).isEqualTo("NO_MATCH_OR_FALLBACK");
        assertThat(review.getAssistantAnswerTextSafe()).doesNotContain("Authorization", "Bearer ");
    }

    @Test
    void recordAssistantTurnStoresActualToolsForSuccessfulBusinessAnswer() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-2");
        dto.setUserQuestion("这些主要存放在哪些库位？");
        dto.setAssistantAnswerTextSafe("当前库存主要存放在以下库位。");
        dto.setActualToolNames(List.of("get_inventory_distribution", "get_inventory_distribution"));

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("COMPLETED");
        assertThat(review.getConfidenceLevel()).isEqualTo("MEDIUM");
        assertThat(review.getActualToolNames()).isEqualTo("get_inventory_distribution");
    }

    @Test
    void submitFeedbackClassifiesWrongIntentAsPlannerReviewAndRegressionNeeded() {
        AgentMessageReview existing = new AgentMessageReview();
        existing.setId(12L);
        existing.setAgentSessionId("session-1");
        existing.setMessageId("msg-3");
        existing.setAnswerStatus("COMPLETED");
        when(reviewMapper.selectOne(any())).thenReturn(existing);

        AgentMessageReviewFeedbackDTO dto = new AgentMessageReviewFeedbackDTO();
        dto.setFeedbackType("WRONG_INTENT");
        dto.setExpectedIntentSummary("查询全部产品最近90天不合格库存，按产品分组");
        dto.setExpectedToolNames(List.of("get_inventory_distribution"));

        service.submitFeedback(loginUser, "session-1", "msg-3", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).updateById(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getFailureDomain()).isEqualTo("PLANNER");
        assertThat(review.getFailureCategory()).isEqualTo("INTENT_MISS");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_PLANNER");
        assertThat(review.getTestCaseStatus()).isEqualTo("NEEDED");
        assertThat(review.getExpectedToolNames()).isEqualTo("get_inventory_distribution");
    }

    @Test
    void submitFeedbackClassifiesAutoDetectedUserCorrection() {
        AgentMessageReview existing = new AgentMessageReview();
        existing.setId(13L);
        existing.setAgentSessionId("session-1");
        existing.setMessageId("msg-4");
        when(reviewMapper.selectOne(any())).thenReturn(existing);

        AgentMessageReviewFeedbackDTO dto = new AgentMessageReviewFeedbackDTO();
        dto.setFeedbackType("USER_CORRECTION");
        dto.setFeedbackNote("用户下一轮纠正：不是这个，我问的是全部产品");
        dto.setExpectedIntentSummary("不是这个，我问的是全部产品");

        service.submitFeedback(loginUser, "session-1", "msg-4", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).updateById(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getFailureDomain()).isEqualTo("USER_INPUT");
        assertThat(review.getFailureCategory()).isEqualTo("USER_CORRECTION_DETECTED");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_PLANNER");
        assertThat(review.getTestCaseStatus()).isEqualTo("NEEDED");
    }
}
