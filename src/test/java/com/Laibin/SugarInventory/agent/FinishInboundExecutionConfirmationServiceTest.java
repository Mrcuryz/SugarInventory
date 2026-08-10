package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionControlCodec;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionAuditService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionConfirmationService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionPreviewArchiveService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionStateVerifier;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionConfirmationMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInboundExecutionConfirmationServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Set<String> AUTHORITIES = Set.of("task:view", "task:confirm");
    private static final Set<String> S3_AUTHORITIES = Set.of(
            "task:view", "task:confirm", "agent:finish-inbound:execute");

    @Test
    void confirmationBindsPreviewUserSessionPermissionAndStoresOnlyCredentialHashes() {
        Fixture fixture = new Fixture();
        AgentFinishInboundExecutionPreview preview = fixture.preview();
        when(fixture.archive.loadOwnedActive(preview.getPreviewRef(), 7, "session-1", AUTHORITIES))
                .thenReturn(new FinishInboundExecutionPreviewArchiveService.StoredPreview(
                        preview, new ObjectMapper().createObjectNode(), new ObjectMapper().createObjectNode()));
        when(fixture.previewMapper.selectByRefForUpdate(preview.getPreviewRef())).thenReturn(preview);
        when(fixture.confirmationMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<AgentFinishInboundExecutionConfirmation>getArgument(0).setId(22L);
            return 1;
        });

        var result = fixture.service.confirm(preview.getPreviewRef(), user(7), "session-1", AUTHORITIES);

        assertThat(result.getConfirmationRef()).startsWith("fic1_");
        assertThat(result.getExecutionToken()).startsWith("fiet1_");
        assertThat(result.getIdempotencyKey()).startsWith("fii1_");
        assertThat(result.isReplayed()).isFalse();
        ArgumentCaptor<AgentFinishInboundExecutionConfirmation> captor =
                ArgumentCaptor.forClass(AgentFinishInboundExecutionConfirmation.class);
        verify(fixture.confirmationMapper).insert(captor.capture());
        AgentFinishInboundExecutionConfirmation stored = captor.getValue();
        assertThat(stored.getOwnerUserId()).isEqualTo(7);
        assertThat(stored.getAgentSessionId()).isEqualTo("session-1");
        assertThat(stored.getRequiredPermissions()).isEqualTo("task:confirm,task:view");
        assertThat(stored.getTokenSha256()).hasSize(64).doesNotContain(result.getExecutionToken());
        assertThat(stored.getIdempotencyKeySha256()).hasSize(64)
                .doesNotContain(result.getIdempotencyKey());
        assertThat(preview.getStatus()).isEqualTo("CONFIRMED");
        verify(fixture.stateVerifier).verifyUnchanged(
                org.mockito.ArgumentMatchers.eq(preview),
                org.mockito.ArgumentMatchers.argThat(value -> value != null && value.getId() == 7));
        verify(fixture.audit).append(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repeatedConfirmationReturnsTheSameServerDerivedCredentials() {
        Fixture fixture = new Fixture();
        AgentFinishInboundExecutionConfirmation confirmation = fixture.confirmation();
        AgentFinishInboundExecutionPreview preview = fixture.preview();
        FinishInboundExecutionControlCodec.Credentials credentials = fixture.codec.credentials(confirmation);
        confirmation.setTokenSha256(FinishInboundExecutionControlCodec.sha256(credentials.executionToken()));
        confirmation.setIdempotencyKeySha256(
                FinishInboundExecutionControlCodec.sha256(credentials.idempotencyKey()));
        when(fixture.confirmationMapper.selectByPreviewRefForUpdate(confirmation.getPreviewRef()))
                .thenReturn(confirmation);
        when(fixture.previewMapper.selectById(confirmation.getPreviewId())).thenReturn(preview);

        var result = fixture.service.confirm(
                confirmation.getPreviewRef(), user(7), "session-1", AUTHORITIES);

        assertThat(result.isReplayed()).isTrue();
        assertThat(result.getExecutionToken()).isEqualTo(credentials.executionToken());
        assertThat(result.getIdempotencyKey()).isEqualTo(credentials.idempotencyKey());
        verify(fixture.confirmationMapper, never()).insert(any());
    }

    @Test
    void domainConfirmationBindsTheDedicatedPermissionAndAllowsCommittedReplay() {
        Fixture fixture = new Fixture();
        AgentFinishInboundExecutionPreview preview = fixture.preview();
        when(fixture.archive.loadOwnedActive(preview.getPreviewRef(), 7, "session-1", S3_AUTHORITIES))
                .thenReturn(new FinishInboundExecutionPreviewArchiveService.StoredPreview(
                        preview, new ObjectMapper().createObjectNode(),
                        new ObjectMapper().createObjectNode()));
        when(fixture.previewMapper.selectByRefForUpdate(preview.getPreviewRef())).thenReturn(preview);
        when(fixture.confirmationMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<AgentFinishInboundExecutionConfirmation>getArgument(0).setId(22L);
            return 1;
        });

        var created = fixture.service.confirmForDomainExecution(
                preview.getPreviewRef(), user(7), "session-1", S3_AUTHORITIES);
        ArgumentCaptor<AgentFinishInboundExecutionConfirmation> captor =
                ArgumentCaptor.forClass(AgentFinishInboundExecutionConfirmation.class);
        verify(fixture.confirmationMapper).insert(captor.capture());
        AgentFinishInboundExecutionConfirmation stored = captor.getValue();
        assertThat(stored.getRequiredPermissions()).isEqualTo(
                "agent:finish-inbound:execute,task:confirm,task:view");

        stored.setStatus("CONSUMED");
        when(fixture.confirmationMapper.selectByPreviewRefForUpdate(preview.getPreviewRef()))
                .thenReturn(stored);
        var replayed = fixture.service.confirmForDomainExecution(
                preview.getPreviewRef(), user(7), "session-1", S3_AUTHORITIES);
        assertThat(replayed.isReplayed()).isTrue();
        assertThat(replayed.getExecutionToken()).isEqualTo(created.getExecutionToken());
        assertThat(replayed.getConfirmationStatusLabel()).contains("已完成");
    }

    @Test
    void changedStateInvalidatesAnExistingConfirmation() {
        Fixture fixture = new Fixture();
        AgentFinishInboundExecutionConfirmation confirmation = fixture.confirmation();
        AgentFinishInboundExecutionPreview preview = fixture.preview();
        when(fixture.confirmationMapper.selectByPreviewRefForUpdate(confirmation.getPreviewRef()))
                .thenReturn(confirmation);
        when(fixture.previewMapper.selectById(confirmation.getPreviewId())).thenReturn(preview);
        doThrow(new BusinessException(409, "状态变化"))
                .when(fixture.stateVerifier).verifyUnchanged(
                        org.mockito.ArgumentMatchers.eq(preview), any(User.class));

        assertThatThrownBy(() -> fixture.service.confirm(
                confirmation.getPreviewRef(), user(7), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessage("状态变化");
        assertThat(confirmation.getStatus()).isEqualTo("INVALIDATED");
        assertThat(preview.getStatus()).isEqualTo("INVALIDATED");
        verify(fixture.audit).append(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("STATE_INVALIDATED"), any(), any(), any(), any(), any());
    }

    @Test
    void crossUserAndConsumedConfirmationCannotBeRevoked() {
        Fixture fixture = new Fixture();
        AgentFinishInboundExecutionConfirmation confirmation = fixture.confirmation();
        when(fixture.confirmationMapper.selectByRefForUpdate(confirmation.getConfirmationRef()))
                .thenReturn(confirmation);

        assertThatThrownBy(() -> fixture.service.revoke(
                confirmation.getConfirmationRef(), user(8), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前用户");

        confirmation.setStatus("CONSUMED");
        assertThatThrownBy(() -> fixture.service.revoke(
                confirmation.getConfirmationRef(), user(7), "session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能撤销");
    }

    private static User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static final class Fixture {
        private final FinishInboundExecutionPreviewArchiveService archive =
                mock(FinishInboundExecutionPreviewArchiveService.class);
        private final FinishInboundExecutionStateVerifier stateVerifier =
                mock(FinishInboundExecutionStateVerifier.class);
        private final FinishInboundExecutionControlCodec codec =
                new FinishInboundExecutionControlCodec(SECRET);
        private final FinishInboundExecutionAuditService audit =
                mock(FinishInboundExecutionAuditService.class);
        private final AgentFinishInboundExecutionPreviewMapper previewMapper =
                mock(AgentFinishInboundExecutionPreviewMapper.class);
        private final AgentFinishInboundExecutionConfirmationMapper confirmationMapper =
                mock(AgentFinishInboundExecutionConfirmationMapper.class);
        private final FinishInboundExecutionConfirmationService service =
                new FinishInboundExecutionConfirmationService(
                        archive, stateVerifier, codec, audit, previewMapper, confirmationMapper);

        private AgentFinishInboundExecutionPreview preview() {
            AgentFinishInboundExecutionPreview preview = new AgentFinishInboundExecutionPreview();
            preview.setId(11L);
            preview.setPreviewRef("fip1_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");
            preview.setOwnerUserId(7);
            preview.setAgentSessionId("session-1");
            preview.setStatus("READY");
            preview.setRequiredPermissions("task:confirm,task:view");
            preview.setContentSha256("a".repeat(64));
            preview.setStateDigest("b".repeat(64));
            preview.setExpiresAt(LocalDateTime.now().plusMinutes(5));
            return preview;
        }

        private AgentFinishInboundExecutionConfirmation confirmation() {
            AgentFinishInboundExecutionConfirmation confirmation =
                    new AgentFinishInboundExecutionConfirmation();
            confirmation.setId(22L);
            confirmation.setConfirmationRef("fic1_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");
            confirmation.setPreviewId(11L);
            confirmation.setPreviewRef("fip1_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ");
            confirmation.setOwnerUserId(7);
            confirmation.setAgentSessionId("session-1");
            confirmation.setStatus("CONFIRMED");
            confirmation.setRequiredPermissions("task:confirm,task:view");
            confirmation.setPreviewContentSha256("a".repeat(64));
            confirmation.setPreviewStateDigest("b".repeat(64));
            confirmation.setExpiresAt(LocalDateTime.now().plusMinutes(2));
            return confirmation;
        }
    }
}
