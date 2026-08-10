package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionAttemptService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionCompletionService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionNoopOrchestrator;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInboundExecutionNoopOrchestratorTest {
    private static final Set<String> AUTHORITIES = Set.of("task:view", "task:confirm");

    @Test
    void committedReplayReturnsSameResultWithoutCallingAdapterBoundaryAgain() {
        FinishInboundExecutionAttemptService attempts = mock(FinishInboundExecutionAttemptService.class);
        FinishInboundExecutionCompletionService completion = mock(FinishInboundExecutionCompletionService.class);
        AgentFinishInboundExecutionRequest request = succeededRequest();
        when(attempts.begin(any(), any(), any(), any(), any(), any()))
                .thenReturn(new FinishInboundExecutionAttemptService.BeginOutcome(request, true));
        FinishInboundExecutionNoopOrchestrator orchestrator =
                new FinishInboundExecutionNoopOrchestrator(attempts, completion);

        var result = orchestrator.consume("fic1_test", "token", "idempotency",
                user(), "session-1", AUTHORITIES);

        assertThat(result.isReplayed()).isTrue();
        assertThat(result.getExecutionRef()).isEqualTo("fie1_same_result");
        assertThat(result.getBusinessWrites()).isZero();
        verify(completion, never()).executeNoop(any());
    }

    @Test
    void unavailableStartAuditFailsClosedBeforeAdapterBoundary() {
        FinishInboundExecutionAttemptService attempts = mock(FinishInboundExecutionAttemptService.class);
        FinishInboundExecutionCompletionService completion = mock(FinishInboundExecutionCompletionService.class);
        when(attempts.begin(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("audit unavailable"));
        FinishInboundExecutionNoopOrchestrator orchestrator =
                new FinishInboundExecutionNoopOrchestrator(attempts, completion);

        assertThatThrownBy(() -> orchestrator.consume("fic1_test", "token", "idempotency",
                user(), "session-1", AUTHORITIES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
        verify(completion, never()).executeNoop(any());
    }

    private static User user() {
        User user = new User();
        user.setId(7);
        return user;
    }

    private static AgentFinishInboundExecutionRequest succeededRequest() {
        AgentFinishInboundExecutionRequest request = new AgentFinishInboundExecutionRequest();
        request.setId(33L);
        request.setExecutionRef("fie1_same_result");
        request.setStatus("SUCCEEDED");
        request.setResultCode("S2_NOOP_VALIDATED");
        request.setAttemptCount(1);
        request.setCompletedAt(LocalDateTime.now());
        return request;
    }
}
