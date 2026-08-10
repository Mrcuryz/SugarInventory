package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionAuditService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionCompletionService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionDomainAdapter;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionNoopAdapter;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionStateVerifier;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionConfirmationMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionRequestMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInboundExecutionCompletionServiceTest {

    private static final Set<String> S3_AUTHORITIES = Set.of(
            "task:view", "task:confirm", "agent:finish-inbound:execute");

    @Test
    void successConsumesConfirmationOnlyAfterNoopAndAuditSucceed() {
        Fixture fixture = new Fixture();

        var result = fixture.service.executeNoop(fixture.request.getId());

        assertThat(result.businessWrites()).isZero();
        assertThat(result.request().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.request().getResultJson()).contains(
                "S2_NOOP_VALIDATED", "\"businessWrites\":0");
        assertThat(fixture.confirmation.getStatus()).isEqualTo("CONSUMED");
        assertThat(fixture.preview.getStatus()).isEqualTo("CONSUMED");
        verify(fixture.adapter).execute(
                fixture.confirmation.getConfirmationRef(), fixture.request.getExecutionRef());
        var lockOrder = inOrder(fixture.confirmationMapper, fixture.requestMapper);
        lockOrder.verify(fixture.confirmationMapper)
                .selectByRefForUpdate(fixture.confirmation.getConfirmationRef());
        lockOrder.verify(fixture.requestMapper).selectByIdForUpdate(fixture.request.getId());
        verify(fixture.audit).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void adapterFailureDoesNotConsumeConfirmation() {
        Fixture fixture = new Fixture();
        when(fixture.adapter.execute(any(), any())).thenThrow(new IllegalStateException("adapter failed"));

        assertThatThrownBy(() -> fixture.service.executeNoop(fixture.request.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("adapter failed");
        assertThat(fixture.confirmation.getStatus()).isEqualTo("CONFIRMED");
        assertThat(fixture.preview.getStatus()).isEqualTo("CONFIRMED");
        verify(fixture.audit, never()).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void auditFailureBlocksConsumptionAndLeavesBusinessWritesAtZero() {
        Fixture fixture = new Fixture();
        doThrow(new IllegalStateException("audit unavailable")).when(fixture.audit)
                .append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> fixture.service.executeNoop(fixture.request.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");
        assertThat(fixture.confirmation.getStatus()).isEqualTo("CONFIRMED");
        assertThat(fixture.preview.getStatus()).isEqualTo("CONFIRMED");
        assertThat(fixture.request.getStatus()).isEqualTo("IN_PROGRESS");
        verify(fixture.adapter).execute(any(), any());
    }

    @Test
    void domainWriteAuditAndCredentialConsumptionShareTheCompletionBoundary() {
        Fixture fixture = new Fixture();
        fixture.confirmation.setRequiredPermissions(
                "agent:finish-inbound:execute,task:confirm,task:view");
        when(fixture.domainAdapter.execute(
                org.mockito.ArgumentMatchers.eq(fixture.preview), any()))
                .thenReturn(new FinishInboundExecutionDomainAdapter.AdapterResult(
                        "FINISH_INBOUND_COMPLETED", 1, List.of("BT0014LU")));

        var result = fixture.service.executeDomain(
                fixture.request.getId(), user(), S3_AUTHORITIES);

        assertThat(result.request().getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.request().getResultJson())
                .contains("FINISH_INBOUND_COMPLETED", "BT0014LU");
        assertThat(fixture.confirmation.getStatus()).isEqualTo("CONSUMED");
        assertThat(fixture.preview.getStatus()).isEqualTo("CONSUMED");
        var order = inOrder(fixture.stateVerifier, fixture.domainAdapter, fixture.audit,
                fixture.requestMapper, fixture.confirmationMapper);
        order.verify(fixture.stateVerifier).verifyUnchanged(
                org.mockito.ArgumentMatchers.eq(fixture.preview), any());
        order.verify(fixture.domainAdapter).execute(
                org.mockito.ArgumentMatchers.eq(fixture.preview), any());
        order.verify(fixture.audit).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        order.verify(fixture.requestMapper).updateById(fixture.request);
        order.verify(fixture.confirmationMapper).updateById(fixture.confirmation);
    }

    private static com.Laibin.SugarInventory.domain.po.User user() {
        var user = new com.Laibin.SugarInventory.domain.po.User();
        user.setId(7);
        return user;
    }

    private static final class Fixture {
        private final AgentFinishInboundExecutionRequestMapper requestMapper =
                mock(AgentFinishInboundExecutionRequestMapper.class);
        private final AgentFinishInboundExecutionConfirmationMapper confirmationMapper =
                mock(AgentFinishInboundExecutionConfirmationMapper.class);
        private final AgentFinishInboundExecutionPreviewMapper previewMapper =
                mock(AgentFinishInboundExecutionPreviewMapper.class);
        private final FinishInboundExecutionAuditService audit =
                mock(FinishInboundExecutionAuditService.class);
        private final FinishInboundExecutionNoopAdapter adapter =
                mock(FinishInboundExecutionNoopAdapter.class);
        private final FinishInboundExecutionDomainAdapter domainAdapter =
                mock(FinishInboundExecutionDomainAdapter.class);
        private final FinishInboundExecutionStateVerifier stateVerifier =
                mock(FinishInboundExecutionStateVerifier.class);
        private final AgentFinishInboundExecutionRequest request = request();
        private final AgentFinishInboundExecutionConfirmation confirmation = confirmation();
        private final AgentFinishInboundExecutionPreview preview = preview();
        private final FinishInboundExecutionCompletionService service;

        private Fixture() {
            when(requestMapper.selectById(request.getId())).thenReturn(request);
            when(requestMapper.selectByIdForUpdate(request.getId())).thenReturn(request);
            when(confirmationMapper.selectByRefForUpdate(request.getConfirmationRef()))
                    .thenReturn(confirmation);
            when(previewMapper.selectById(confirmation.getPreviewId())).thenReturn(preview);
            when(adapter.execute(any(), any())).thenReturn(
                    new FinishInboundExecutionNoopAdapter.AdapterResult("S2_NOOP_VALIDATED", 0));
            service = new FinishInboundExecutionCompletionService(
                    requestMapper, confirmationMapper, previewMapper, audit, adapter,
                    domainAdapter, stateVerifier,
                    new ObjectMapper().findAndRegisterModules());
        }

        private static AgentFinishInboundExecutionRequest request() {
            AgentFinishInboundExecutionRequest value = new AgentFinishInboundExecutionRequest();
            value.setId(33L);
            value.setExecutionRef("fie1_test");
            value.setConfirmationId(22L);
            value.setConfirmationRef("fic1_test");
            value.setOwnerUserId(7);
            value.setAgentSessionId("session-1");
            value.setStatus("IN_PROGRESS");
            value.setAttemptCount(1);
            value.setStartedAt(LocalDateTime.now());
            return value;
        }

        private static AgentFinishInboundExecutionConfirmation confirmation() {
            AgentFinishInboundExecutionConfirmation value = new AgentFinishInboundExecutionConfirmation();
            value.setId(22L);
            value.setConfirmationRef("fic1_test");
            value.setPreviewId(11L);
            value.setStatus("CONFIRMED");
            return value;
        }

        private static AgentFinishInboundExecutionPreview preview() {
            AgentFinishInboundExecutionPreview value = new AgentFinishInboundExecutionPreview();
            value.setId(11L);
            value.setStatus("CONFIRMED");
            return value;
        }
    }
}
