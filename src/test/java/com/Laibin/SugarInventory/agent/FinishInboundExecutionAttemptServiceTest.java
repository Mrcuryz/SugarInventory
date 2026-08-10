package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionControlCodec;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionAttemptService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionAuditService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionStateVerifier;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionConfirmationMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionRequestMapper;
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

class FinishInboundExecutionAttemptServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Set<String> AUTHORITIES = Set.of("task:view", "task:confirm");

    @Test
    void acceptsBoundTokenOnceAndCreatesServerFingerprint() {
        Fixture fixture = new Fixture();
        when(fixture.requestMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<AgentFinishInboundExecutionRequest>getArgument(0).setId(33L);
            return 1;
        });

        var result = fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(7), "session-1", AUTHORITIES);

        assertThat(result.replayed()).isFalse();
        assertThat(result.request().getExecutionRef()).startsWith("fie1_");
        assertThat(result.request().getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.request().getStartedAt()).isNotNull();
        assertThat(result.request().getUpdatedAt()).isNotNull();
        assertThat(result.request().getRequestSha256()).hasSize(64);
        assertThat(result.request().getIdempotencyKeySha256())
                .isNotEqualTo(fixture.credentials.idempotencyKey());
        verify(fixture.stateVerifier).verifyUnchanged(
                org.mockito.ArgumentMatchers.eq(fixture.preview),
                org.mockito.ArgumentMatchers.argThat(value -> value != null && value.getId() == 7));
        verify(fixture.audit).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void successfulRetryReturnsSameResultWithoutRecheckingOrExecuting() {
        Fixture fixture = new Fixture();
        AgentFinishInboundExecutionRequest request = fixture.succeededRequest();
        when(fixture.requestMapper.selectByConfirmationForUpdate(fixture.confirmation.getId()))
                .thenReturn(request);

        var result = fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(7), "session-1", AUTHORITIES);

        assertThat(result.replayed()).isTrue();
        assertThat(result.request().getExecutionRef()).isEqualTo("fie1_existing");
        verify(fixture.stateVerifier, never()).verifyUnchanged(any(), any());
        verify(fixture.requestMapper, never()).insert(any());
    }

    @Test
    void rejectsCrossUserWrongTokenAndRevokedConfirmation() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(8), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前用户");

        assertThatThrownBy(() -> fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                "fiet1_" + "x".repeat(43), fixture.credentials.idempotencyKey(),
                user(7), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class).hasMessageContaining("令牌无效");

        fixture.confirmation.setStatus("REVOKED");
        assertThatThrownBy(() -> fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(7), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已撤销");
    }

    @Test
    void rejectsExpiredAndCrossSessionConfirmationBeforeCreatingRequest() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(7), "session-2", AUTHORITIES))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不属于当前 Agent 会话");

        fixture.confirmation.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(7), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已过期");
        assertThat(fixture.confirmation.getStatus()).isEqualTo("EXPIRED");
        verify(fixture.requestMapper, never()).insert(any());
    }

    @Test
    void changedBusinessStateInvalidatesTokenBeforeAdapterBoundary() {
        Fixture fixture = new Fixture();
        org.mockito.Mockito.doThrow(new BusinessException(409, "状态变化"))
                .when(fixture.stateVerifier).verifyUnchanged(
                        org.mockito.ArgumentMatchers.eq(fixture.preview), any(User.class));

        assertThatThrownBy(() -> fixture.service.begin(fixture.confirmation.getConfirmationRef(),
                fixture.credentials.executionToken(), fixture.credentials.idempotencyKey(),
                user(7), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class).hasMessage("状态变化");
        assertThat(fixture.confirmation.getStatus()).isEqualTo("INVALIDATED");
        assertThat(fixture.preview.getStatus()).isEqualTo("INVALIDATED");
        verify(fixture.requestMapper, never()).insert(any());
    }

    private static User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final class Fixture {
        private final AgentFinishInboundExecutionConfirmationMapper confirmationMapper =
                mock(AgentFinishInboundExecutionConfirmationMapper.class);
        private final AgentFinishInboundExecutionPreviewMapper previewMapper =
                mock(AgentFinishInboundExecutionPreviewMapper.class);
        private final AgentFinishInboundExecutionRequestMapper requestMapper =
                mock(AgentFinishInboundExecutionRequestMapper.class);
        private final FinishInboundExecutionStateVerifier stateVerifier =
                mock(FinishInboundExecutionStateVerifier.class);
        private final FinishInboundExecutionControlCodec codec =
                new FinishInboundExecutionControlCodec(SECRET);
        private final FinishInboundExecutionAuditService audit =
                mock(FinishInboundExecutionAuditService.class);
        private final AgentFinishInboundExecutionConfirmation confirmation = confirmation();
        private final AgentFinishInboundExecutionPreview preview = preview();
        private final FinishInboundExecutionControlCodec.Credentials credentials = codec.credentials(confirmation);
        private final FinishInboundExecutionAttemptService service;

        private Fixture() {
            confirmation.setTokenSha256(FinishInboundExecutionControlCodec.sha256(credentials.executionToken()));
            confirmation.setIdempotencyKeySha256(
                    FinishInboundExecutionControlCodec.sha256(credentials.idempotencyKey()));
            when(confirmationMapper.selectByRefForUpdate(confirmation.getConfirmationRef()))
                    .thenReturn(confirmation);
            when(previewMapper.selectById(confirmation.getPreviewId())).thenReturn(preview);
            service = new FinishInboundExecutionAttemptService(
                    confirmationMapper, previewMapper, requestMapper, stateVerifier, codec, audit);
        }

        private AgentFinishInboundExecutionRequest succeededRequest() {
            AgentFinishInboundExecutionRequest request = new AgentFinishInboundExecutionRequest();
            request.setId(33L);
            request.setExecutionRef("fie1_existing");
            request.setConfirmationId(confirmation.getId());
            request.setConfirmationRef(confirmation.getConfirmationRef());
            request.setOwnerUserId(7);
            request.setAgentSessionId("session-1");
            request.setIdempotencyKeySha256(
                    FinishInboundExecutionControlCodec.sha256(credentials.idempotencyKey()));
            request.setRequestSha256(FinishInboundExecutionControlCodec.sha256(
                    "S2_NOOP_CONTROL_VALIDATION_V1|" + confirmation.getConfirmationRef() + "|"
                            + confirmation.getPreviewContentSha256() + "|"
                            + confirmation.getPreviewStateDigest()));
            request.setStatus("SUCCEEDED");
            request.setAttemptCount(1);
            return request;
        }

        private static AgentFinishInboundExecutionConfirmation confirmation() {
            AgentFinishInboundExecutionConfirmation value = new AgentFinishInboundExecutionConfirmation();
            value.setId(22L);
            value.setConfirmationRef("fic1_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");
            value.setPreviewId(11L);
            value.setPreviewRef("fip1_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");
            value.setOwnerUserId(7);
            value.setAgentSessionId("session-1");
            value.setStatus("CONFIRMED");
            value.setRequiredPermissions("task:confirm,task:view");
            value.setPreviewContentSha256("a".repeat(64));
            value.setPreviewStateDigest("b".repeat(64));
            value.setExpiresAt(LocalDateTime.now().plusMinutes(2));
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
