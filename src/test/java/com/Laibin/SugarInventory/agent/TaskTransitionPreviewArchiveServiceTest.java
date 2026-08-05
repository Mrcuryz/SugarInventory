package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.TaskTransitionPreviewRefCodec;
import com.Laibin.SugarInventory.agent.service.TaskTransitionPreviewArchiveService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentTaskTransitionPreview;
import com.Laibin.SugarInventory.domain.vo.TaskTransitionPreviewVO;
import com.Laibin.SugarInventory.mapper.AgentTaskTransitionPreviewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskTransitionPreviewArchiveServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Set<String> AUTHORITIES = Set.of("task:view", "task:confirm", "inventory:view");

    @Test
    void persistsOnlyReadyPreviewAsImmutableTechnicalSnapshot() {
        AgentTaskTransitionPreviewMapper mapper = mock(AgentTaskTransitionPreviewMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        TaskTransitionPreviewArchiveService service = service(mapper);
        TaskTransitionPreviewVO preview = readyPreview();

        TaskTransitionPreviewVO result = service.persistReady(preview, 7, "agt-session-1", AUTHORITIES);

        assertThat(result).isSameAs(preview);
        ArgumentCaptor<AgentTaskTransitionPreview> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionPreview.class);
        verify(mapper).insert(captor.capture());
        AgentTaskTransitionPreview stored = captor.getValue();
        assertThat(stored.getPreviewRef()).isEqualTo(preview.getPreviewRef());
        assertThat(stored.getOwnerUserId()).isEqualTo(7);
        assertThat(stored.getAgentSessionId()).isEqualTo("agt-session-1");
        assertThat(stored.getTransitionType()).isEqualTo("CONFIRM_FINISH_INBOUND");
        assertThat(stored.getStatus()).isEqualTo("READY");
        assertThat(stored.getRequiredPermissions()).isEqualTo("task:confirm,task:view");
        assertThat(stored.getRequiredPermissions()).doesNotContain("inventory:view");
        assertThat(stored.getEntityRefsJson()).isEqualTo("[\"BT0019N1\"]");
        assertThat(stored.getPayloadJson()).contains("BT0019N1", "CONFIRM_FINISH_INBOUND");
        assertThat(stored.getStateDigest()).hasSize(64);
        assertThat(stored.getNormalizedRequestSha256()).hasSize(64);
        assertThat(stored.getContentSha256()).hasSize(64);
        assertThat(stored.getRevokedAt()).isNull();
        assertThat(stored.getConsumedAt()).isNull();
    }

    @Test
    void doesNotPersistConflictPreview() {
        AgentTaskTransitionPreviewMapper mapper = mock(AgentTaskTransitionPreviewMapper.class);
        TaskTransitionPreviewArchiveService service = service(mapper);
        TaskTransitionPreviewVO conflict = TaskTransitionPreviewVO.builder()
                .dataScope("CURRENT_FINISH_INBOUND_TASK_TRANSITION_PREVIEW")
                .previewVersion(1)
                .previewStatus("CONFLICT")
                .transition("CONFIRM_FINISH_INBOUND")
                .transitionLabel("确认成品入库")
                .canOpenBusinessDialog(false)
                .requestedTaskCount(1)
                .eligibleTaskCount(0)
                .tasks(List.of())
                .requiredUserInputs(List.of())
                .blockingIssues(List.of("任务已变化"))
                .warnings(List.of())
                .limitations(List.of("未执行写入"))
                .build();

        assertThat(service.persistReady(conflict, 7, "agt-session-1", AUTHORITIES))
                .isSameAs(conflict);
        verify(mapper, never()).insert(any());
    }

    @Test
    void persistedPreviewCanBeVerifiedAfterServiceRecreation() {
        AgentTaskTransitionPreviewMapper mapper = mock(AgentTaskTransitionPreviewMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        TaskTransitionPreviewArchiveService firstService = service(mapper);
        firstService.persistReady(readyPreview(), 7, "agt-session-1", AUTHORITIES);

        ArgumentCaptor<AgentTaskTransitionPreview> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionPreview.class);
        verify(mapper).insert(captor.capture());
        AgentTaskTransitionPreview stored = captor.getValue();
        when(mapper.selectOne(any())).thenReturn(stored);

        TaskTransitionPreviewArchiveService restartedService = service(mapper);
        TaskTransitionPreviewArchiveService.StoredPreview loaded = restartedService.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-1", AUTHORITIES);

        assertThat(loaded.row()).isSameAs(stored);
        assertThat(loaded.snapshot().path("transition").asText())
                .isEqualTo("CONFIRM_FINISH_INBOUND");
        assertThat(loaded.snapshot().path("tasks").get(0).path("palletCode").asText())
                .isEqualTo("BT0019N1");
    }

    @Test
    void rejectsWrongOwnerSessionPermissionExpiryAndTampering() {
        AgentTaskTransitionPreviewMapper mapper = mock(AgentTaskTransitionPreviewMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        TaskTransitionPreviewArchiveService service = service(mapper);
        TaskTransitionPreviewVO preview = readyPreview();
        service.persistReady(preview, 7, "agt-session-1", AUTHORITIES);
        ArgumentCaptor<AgentTaskTransitionPreview> captor =
                ArgumentCaptor.forClass(AgentTaskTransitionPreview.class);
        verify(mapper).insert(captor.capture());
        AgentTaskTransitionPreview stored = captor.getValue();
        when(mapper.selectOne(any())).thenReturn(stored);

        assertThatThrownBy(() -> service.loadOwnedActive(
                stored.getPreviewRef(), 8, "agt-session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前用户");
        assertThatThrownBy(() -> service.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-2", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前 Agent 会话");
        assertThatThrownBy(() -> service.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-1", Set.of("task:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有查看或继续");

        stored.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(() -> service.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");

        stored.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        stored.setPayloadJson(stored.getPayloadJson().replace("BT0019N1", "BT9999ZZ"));
        assertThatThrownBy(() -> service.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容校验失败");
    }

    @Test
    void migrationDefinesPersistenceWithoutBusinessExecuteCapability() throws Exception {
        String sql = Files.readString(Path.of(
                "migrations", "2026-08-05-add-agent-task-transition-preview.sql"));

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS agent_task_transition_preview",
                "preview_ref VARCHAR(64) NOT NULL",
                "owner_user_id INT NOT NULL",
                "agent_session_id VARCHAR(64)",
                "normalized_request_sha256 CHAR(64) NOT NULL",
                "content_sha256 CHAR(64) NOT NULL",
                "expires_at DATETIME(6) NOT NULL");
        assertThat(sql).doesNotContain("execution_token", "idempotency_key");
    }

    private static TaskTransitionPreviewArchiveService service(
            AgentTaskTransitionPreviewMapper mapper) {
        return new TaskTransitionPreviewArchiveService(
                mapper,
                new ObjectMapper().findAndRegisterModules());
    }

    private static TaskTransitionPreviewVO readyPreview() {
        LocalDateTime previewedAt = LocalDateTime.now().plusSeconds(1);
        LocalDateTime expiresAt = previewedAt.plusMinutes(5);
        String digest = "a".repeat(64);
        String previewRef = new TaskTransitionPreviewRefCodec(SECRET)
                .encode(7, digest, expiresAt);
        return TaskTransitionPreviewVO.builder()
                .dataScope("CURRENT_FINISH_INBOUND_TASK_TRANSITION_PREVIEW")
                .previewVersion(1)
                .previewStatus("READY")
                .previewRef(previewRef)
                .stateDigest(digest)
                .previewedAt(previewedAt)
                .expiresAt(expiresAt)
                .transition("CONFIRM_FINISH_INBOUND")
                .transitionLabel("确认成品入库")
                .canOpenBusinessDialog(true)
                .requestedTaskCount(1)
                .eligibleTaskCount(1)
                .tasks(List.of(TaskTransitionPreviewVO.Task.builder()
                        .palletCode("BT0019N1")
                        .currentTaskStatus("PENDING")
                        .productName("黄冰糖（袋）")
                        .productionDate(LocalDate.of(2026, 5, 22))
                        .totalWeight(new BigDecimal("1000.0"))
                        .build()))
                .requiredUserInputs(List.of("入库库位"))
                .blockingIssues(List.of())
                .warnings(List.of("提交时重新校验"))
                .limitations(List.of("未执行写入"))
                .build();
    }
}
