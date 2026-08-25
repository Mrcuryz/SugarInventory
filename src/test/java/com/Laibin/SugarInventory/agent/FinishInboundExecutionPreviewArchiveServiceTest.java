package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.model.FinishInboundExecutionPreviewSnapshot;
import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionPreviewRefCodec;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionPreviewArchiveService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPreviewVO;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInboundExecutionPreviewArchiveServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final Set<String> AUTHORITIES = Set.of("task:view", "task:confirm");

    @Test
    void warehouseDedicatedPermissionCanArchiveAndReloadWithoutGenericTaskConfirm() {
        AgentFinishInboundExecutionPreviewMapper mapper = mock(
                AgentFinishInboundExecutionPreviewMapper.class);
        FinishInboundExecutionPreviewArchiveService service = service(mapper);
        Set<String> warehouseAuthorities = Set.of(
                "task:view", "agent:finish-inbound:execute");
        FinishInboundExecutionPreviewVO preview = readyPreview();
        when(mapper.insert(any())).thenReturn(1);

        service.persistReady(preview, 7, "session-1", warehouseAuthorities);

        ArgumentCaptor<AgentFinishInboundExecutionPreview> captor =
                ArgumentCaptor.forClass(AgentFinishInboundExecutionPreview.class);
        verify(mapper).insert(captor.capture());
        AgentFinishInboundExecutionPreview stored = captor.getValue();
        assertThat(stored.getRequiredPermissions())
                .isEqualTo("agent:finish-inbound:execute,task:view");
        when(mapper.selectOne(any())).thenReturn(stored);
        assertThat(service.loadOwnedActive(preview.getPreviewRef(), 7,
                "session-1", warehouseAuthorities).row()).isSameAs(stored);
    }

    @Test
    void storesInternalIdsOnlyInServerSnapshotAndKeepsPublicPayloadSafe() {
        AgentFinishInboundExecutionPreviewMapper mapper = mock(AgentFinishInboundExecutionPreviewMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        FinishInboundExecutionPreviewArchiveService service = service(mapper);
        FinishInboundExecutionPreviewVO preview = readyPreview();

        service.persistReady(preview, 7, "agt-session-1", AUTHORITIES);

        ArgumentCaptor<AgentFinishInboundExecutionPreview> captor =
                ArgumentCaptor.forClass(AgentFinishInboundExecutionPreview.class);
        verify(mapper).insert(captor.capture());
        AgentFinishInboundExecutionPreview stored = captor.getValue();
        assertThat(stored.getPreviewRef()).startsWith("fip1_");
        assertThat(stored.getAgentSessionId()).isEqualTo("agt-session-1");
        assertThat(stored.getRequiredPermissions()).isEqualTo("task:confirm,task:view");
        assertThat(stored.getEntityRefsJson()).isEqualTo("[\"BT0019N1\"]");
        assertThat(stored.getPublicPayloadJson())
                .contains("BT0019N1", "1号库位")
                .doesNotContain("serverSnapshot", "taskId", "warehouseId", "inventoryId", "\"id\":21");
        assertThat(stored.getEntityStateJson())
                .contains("\"task\"", "\"id\":21", "\"warehouse\"", "\"id\":41");
        assertThat(stored.getStateDigest()).hasSize(64);
        assertThat(stored.getNormalizedRequestSha256()).hasSize(64);
        assertThat(stored.getContentSha256()).hasSize(64);
    }

    @Test
    void reloadVerifiesOwnerSessionPermissionAndStoredHashes() {
        AgentFinishInboundExecutionPreviewMapper mapper = mock(AgentFinishInboundExecutionPreviewMapper.class);
        when(mapper.insert(any())).thenReturn(1);
        FinishInboundExecutionPreviewArchiveService service = service(mapper);
        service.persistReady(readyPreview(), 7, "agt-session-1", AUTHORITIES);
        ArgumentCaptor<AgentFinishInboundExecutionPreview> captor =
                ArgumentCaptor.forClass(AgentFinishInboundExecutionPreview.class);
        verify(mapper).insert(captor.capture());
        AgentFinishInboundExecutionPreview stored = captor.getValue();
        when(mapper.selectOne(any())).thenReturn(stored);

        FinishInboundExecutionPreviewArchiveService.StoredPreview loaded = service.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-1", AUTHORITIES);

        assertThat(loaded.publicPayload().path("previewStatus").asText()).isEqualTo("READY");
        assertThat(loaded.entityState().path("items").get(0).path("task").path("id").asInt())
                .isEqualTo(21);
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
                .hasMessageContaining("没有查看或生成");

        stored.setPublicPayloadJson(stored.getPublicPayloadJson().replace("BT0019N1", "BT9999ZZ"));
        assertThatThrownBy(() -> service.loadOwnedActive(
                stored.getPreviewRef(), 7, "agt-session-1", AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容校验失败");
    }

    @Test
    void refusesToPersistPreviewWithoutAgentSessionBinding() {
        AgentFinishInboundExecutionPreviewMapper mapper = mock(AgentFinishInboundExecutionPreviewMapper.class);
        FinishInboundExecutionPreviewArchiveService service = service(mapper);

        assertThatThrownBy(() -> service.persistReady(readyPreview(), 7, null, AUTHORITIES))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话引用不能为空");
    }

    @Test
    void migrationStoresOnlyTechnicalPreviewStateWithoutExecutionCapability() throws Exception {
        String sql = Files.readString(Path.of(
                "migrations", "2026-08-09-add-agent-finish-inbound-execution-preview.sql"));

        assertThat(sql).contains(
                "CREATE TABLE IF NOT EXISTS agent_finish_inbound_execution_preview",
                "normalized_input_json",
                "entity_state_json",
                "public_payload_json",
                "content_sha256",
                "expires_at");
        assertThat(sql).doesNotContain(
                "execution_token", "idempotency_key", "confirmation_ref", "confirmed_at");
    }

    private static FinishInboundExecutionPreviewArchiveService service(
            AgentFinishInboundExecutionPreviewMapper mapper) {
        return new FinishInboundExecutionPreviewArchiveService(
                mapper,
                new FinishInboundExecutionPreviewRefCodec(SECRET),
                new ObjectMapper().findAndRegisterModules());
    }

    private static FinishInboundExecutionPreviewVO readyPreview() {
        LocalDateTime previewedAt = LocalDateTime.now().plusSeconds(1);
        FinishInboundExecutionPreviewSnapshot.NormalizedInput normalizedInput =
                FinishInboundExecutionPreviewSnapshot.NormalizedInput.builder()
                        .code("BT0019N1")
                        .warehouseName("1号库位")
                        .entryDate(LocalDate.of(2026, 8, 9))
                        .side("左")
                        .quantity(1)
                        .unit("0")
                        .remark("验收预览")
                        .build();
        FinishInboundExecutionPreviewSnapshot.Item snapshotItem =
                FinishInboundExecutionPreviewSnapshot.Item.builder()
                        .normalizedInput(normalizedInput)
                        .task(FinishInboundExecutionPreviewSnapshot.TaskState.builder()
                                .id(21).taskType("FINISH_IN").status("PENDING").cycleNo(2).build())
                        .pallet(FinishInboundExecutionPreviewSnapshot.PalletState.builder()
                                .id(11).status("PENDING").currentCycleNo(2).productId(31).build())
                        .product(FinishInboundExecutionPreviewSnapshot.ProductState.builder()
                                .id(31).status("ENABLED").piecesPerPallet(40).canStack(true).build())
                        .warehouse(FinishInboundExecutionPreviewSnapshot.WarehouseState.builder()
                                .id(41).status("EMPTY").build())
                        .materialInputs(List.of())
                        .materialBalances(List.of())
                        .build();
        FinishInboundExecutionPreviewSnapshot snapshot =
                FinishInboundExecutionPreviewSnapshot.builder()
                        .previewVersion(1)
                        .items(List.of(snapshotItem))
                        .build();
        return FinishInboundExecutionPreviewVO.builder()
                .dataScope("FINISH_INBOUND_EXECUTION_PREVIEW")
                .previewVersion(1)
                .previewStatus("READY")
                .previewedAt(previewedAt)
                .expiresAt(previewedAt.plusMinutes(5))
                .readyForUserConfirmation(true)
                .requestedItemCount(1)
                .eligibleItemCount(1)
                .items(List.of(FinishInboundExecutionPreviewVO.Item.builder()
                        .palletCode("BT0019N1")
                        .productName("黄冰糖（袋）")
                        .productionDate(LocalDate.of(2026, 8, 8))
                        .warehouseName("1号库位")
                        .entryDate(LocalDate.of(2026, 8, 9))
                        .side("左")
                        .quantity(1)
                        .unitLabel("板")
                        .remark("验收预览")
                        .build()))
                .blockingIssues(List.of())
                .warnings(List.of("最终执行前重新校验"))
                .limitations(List.of("未执行写入"))
                .serverSnapshot(snapshot)
                .build();
    }
}
