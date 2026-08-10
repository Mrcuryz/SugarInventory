package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionAttemptService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionCompletionService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionDomainOrchestrator;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInboundExecutionDomainOrchestratorTest {

    @Test
    void committedRequestReplaysTheSameSafeResultWithoutCallingDomainAgain() {
        FinishInboundExecutionAttemptService attempt = mock(FinishInboundExecutionAttemptService.class);
        FinishInboundExecutionCompletionService completion =
                mock(FinishInboundExecutionCompletionService.class);
        AgentFinishInboundExecutionRequest request = new AgentFinishInboundExecutionRequest();
        request.setId(33L);
        request.setStatus("SUCCEEDED");
        request.setResultJson("""
                {"resultCode":"FINISH_INBOUND_COMPLETED","affectedPalletCount":1,
                 "palletCodes":["BT0014LU"],"resultLabel":"成品入库已完成"}
                """);
        request.setCompletedAt(LocalDateTime.now());
        when(attempt.beginDomainExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FinishInboundExecutionAttemptService.BeginOutcome(request, true));
        FinishInboundExecutionDomainOrchestrator orchestrator =
                new FinishInboundExecutionDomainOrchestrator(
                        attempt, completion, new ObjectMapper());

        var result = orchestrator.execute("fic1_test", "token", "key", user(),
                "session-1", Set.of("task:view", "task:confirm",
                        "agent:finish-inbound:execute"));

        assertThat(result.isReplayed()).isTrue();
        assertThat(result.getStatusLabel()).isEqualTo("成品入库已完成");
        assertThat(result.getPalletCodes()).containsExactly("BT0014LU");
        verify(completion, never()).executeDomain(any(), any(), any());
    }

    private static User user() {
        User user = new User();
        user.setId(7);
        return user;
    }
}
