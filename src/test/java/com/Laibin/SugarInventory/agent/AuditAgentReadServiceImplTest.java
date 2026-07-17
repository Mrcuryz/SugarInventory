package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AuditAgentQueries;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.impl.AuditAgentReadServiceImpl;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewListVO;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.mapper.AgentToolAuditLogMapper;
import com.Laibin.SugarInventory.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditAgentReadServiceImplTest {
    @Test
    void operationLogsExposeFieldNamesButNeverOldOrChangedValues() {
        OperationLogService logs = mock(OperationLogService.class);
        OperationLog row = new OperationLog(); row.setTableName("inventory"); row.setOperationType("UPDATE"); row.setOperator("张三");
        row.setChangedFields("{\"quantity\":100,\"password\":\"secret\"}"); row.setOldData("{\"quantity\":1}");
        when(logs.queryOperationLogs(any())).thenReturn(new PageResult<>(1L, List.of(row)));
        var service = new AuditAgentReadServiceImpl(logs, mock(AgentToolAuditLogMapper.class), mock(AgentMessageReviewService.class), new ObjectMapper());

        var result = service.searchOperationLogs(new AuditAgentQueries.OperationLogs());

        assertThat(result.getRecords()).singleElement().satisfies(item -> assertThat(item.getChangedFieldNames()).containsExactly("quantity", "password"));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("字段值"));
        assertThat(result.toString()).doesNotContain("secret", "\"quantity\":1", "\"quantity\":100");
    }

    @Test
    void answerReviewSummaryDropsQuestionsAnswersToolsAndInternalIds() {
        AgentMessageReviewService reviews = mock(AgentMessageReviewService.class);
        AgentMessageReviewListVO row = new AgentMessageReviewListVO(); row.setId(99L); row.setUserQuestionSummary("敏感问题");
        row.setAssistantAnswerSummary("敏感回答"); row.setActualToolNames(List.of("internal_tool")); row.setAnswerStatus("NEEDS_REVIEW"); row.setReviewStatus("OPEN");
        when(reviews.pageReviews(any(), any())).thenReturn(new PageResult<>(1L, List.of(row)));
        var service = new AuditAgentReadServiceImpl(mock(OperationLogService.class), mock(AgentToolAuditLogMapper.class), reviews, new ObjectMapper());

        var result = service.queryAgentAnswerReviews(mock(LoginUser.class), new AuditAgentQueries.AnswerReviews());

        assertThat(result.toString()).doesNotContain("敏感问题", "敏感回答", "internal_tool", "99");
        assertThat(result.getRecords()).singleElement().satisfies(item -> assertThat(item.getReviewStatus()).isEqualTo("OPEN"));
    }
}
