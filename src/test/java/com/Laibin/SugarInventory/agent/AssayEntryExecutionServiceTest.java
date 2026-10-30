package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.security.AssayEntryControlCodec;
import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.AssayEntryConfirmationVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryExecutionServiceTest {
    private final AssayEntryArchiveService archive = mock(AssayEntryArchiveService.class);
    private final AgentAssayEntryConfirmationMapper confirmations = mock(AgentAssayEntryConfirmationMapper.class);
    private final AgentAssayEntryPreviewMapper previews = mock(AgentAssayEntryPreviewMapper.class);
    private final AgentAssayEntryAuditMapper audit = mock(AgentAssayEntryAuditMapper.class);
    private final AssayWriteService writer = mock(AssayWriteService.class);
    private final AssayEntryControlCodec codec = new AssayEntryControlCodec("test-only-assay-control-key-at-least-32-characters");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AssayEntryExecutionService service = new AssayEntryExecutionService(archive, confirmations, previews, audit, codec, writer, json);
    private final String ref = "aep1_" + "a".repeat(43);
    private AgentAssayEntryPreview preview;
    private AgentAssayEntryConfirmation stored;
    private AssaySubmitDTO input;
    private AssayJudgeOutcome judgement;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "enabled", true);
        preview = new AgentAssayEntryPreview(); preview.setId(1L); preview.setPreviewRef(ref); preview.setOwnerUserId(7);
        preview.setAgentSessionId("s"); preview.setStatus("READY"); preview.setContentDigest("digest");
        preview.setExpiresAt(LocalDateTime.now().plusMinutes(5)); preview.setPublicPayloadJson("{\"previewOnly\":true,\"warnings\":[]}");
        when(archive.loadOwnedControl(7, "s", ref)).thenReturn(preview);
        when(archive.loadOwnedRecovery(7, "s", ref)).thenReturn(preview);
        input = new AssaySubmitDTO(); input.setProductId(84); input.setSampleDate(LocalDate.of(2026, 9, 24));
        var product = new Product(); product.setId(84); product.setProductName("黄冰糖测试");
        judgement = new AssayJudgeOutcome(); judgement.setJudgeResult("NO_STANDARD");
        when(archive.verifyForExecution(7, "s", ref)).thenReturn(new AssayEntryArchiveService.VerifiedPreview(preview, input,
                new AssayEntryStateService.State(product, null, judgement, "{}")));
        when(confirmations.selectByPreviewForUpdate(ref)).thenAnswer(call -> stored);
        when(confirmations.insert(any(AgentAssayEntryConfirmation.class))).thenAnswer(call -> { stored = call.getArgument(0); stored.setId(1L); return 1; });
        when(confirmations.updateById(any(AgentAssayEntryConfirmation.class))).thenReturn(1);
        when(previews.updateStatus(eq(1L), anyString())).thenAnswer(call -> { preview.setStatus(call.getArgument(1)); return 1; });
        when(audit.append(any(), any(), any(), any(), any(), any())).thenReturn(1);
        var assay = new Assay(); assay.setId(123); assay.setVersion(1); assay.setSampleDate(input.getSampleDate()); assay.setCreatedAt(LocalDateTime.now());
        when(writer.appendVerified(eq(input), eq(7), isNull(), eq(judgement))).thenReturn(assay);
    }

    @Test
    void confirmationIsReplayableButNeverWritesAssay() {
        var first = service.confirm(7, "s", ref); var again = service.confirm(7, "s", ref);
        assertThat(first.executionToken()).isEqualTo(again.executionToken());
        assertThat(first.idempotencyKey()).isEqualTo(again.idempotencyKey()); assertThat(again.replayed()).isTrue();
        verify(confirmations, times(1)).insert(any(AgentAssayEntryConfirmation.class));
        verifyNoInteractions(writer);
    }

    @Test
    void executionRetryAndReadbackReturnSameRecord() {
        var token = token(service.confirm(7, "s", ref));
        var first = service.execute(7, "s", ref, token); var again = service.execute(7, "s", ref, token);
        assertThat(first.saved()).isTrue(); assertThat(first.assayId()).isEqualTo(123);
        assertThat(first.details().path("previewOnly").asBoolean()).isFalse();
        assertThat(again.replayed()).isTrue(); assertThat(again.assayId()).isEqualTo(first.assayId());
        assertThat(service.result(7, "s", ref).assayId()).isEqualTo(123);
        verify(writer, times(1)).appendVerified(input, 7, null, judgement);
        verify(audit, times(1)).append(7, "s", ref, "EXECUTION_SUCCEEDED", 200, 123);
    }

    @Test
    void rejectsMissingConfirmationAndChangedCredentials() {
        var request = new AssayEntryExecutionTokenDTO(); request.setExecutionToken("aet1_"+"b".repeat(43)); request.setIdempotencyKey("aei1_"+"c".repeat(43));
        assertThatThrownBy(() -> service.execute(7, "s", ref, request)).isInstanceOf(BusinessException.class);
        service.confirm(7, "s", ref);
        assertThatThrownBy(() -> service.execute(7, "s", ref, request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void rejectsClientMeasurementsEvenWithValidToken() {
        var token = token(service.confirm(7, "s", ref)); token.rejectAdditionalInput("colorValue", 100);
        assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void rejectsExpiredCanceledOrSupersededBeforeWriting() {
        var token = token(service.confirm(7, "s", ref));
        for (String status : java.util.List.of("CANCELED", "SUPERSEDED", "READY")) {
            preview.setStatus(status);
            assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(BusinessException.class);
        }
        preview.setStatus("CONFIRMED"); stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void stateChangesAfterConfirmationStillPreventSaving() {
        var token = token(service.confirm(7, "s", ref));
        when(archive.verifyForExecution(7, "s", ref)).thenThrow(new BusinessException(1055, "标准已变化"));
        assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
        verifyNoInteractions(writer);
    }

    @Test
    void revokedAccessStopsEvenSuccessfulResultReplay() {
        var token = token(service.confirm(7, "s", ref)); service.execute(7, "s", ref, token);
        when(archive.loadOwnedControl(7, "s", ref)).thenThrow(new BusinessException(1056, "权限已撤销"));
        assertThatThrownBy(() -> service.result(7, "s", ref)).isInstanceOf(BusinessException.class);
    }

    @Test
    void auditFailureIsNotReportedAsSuccessfulSave() {
        var token = token(service.confirm(7, "s", ref));
        when(audit.append(7, "s", ref, "EXECUTION_SUCCEEDED", 200, 123)).thenReturn(0);
        assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void featureDefaultsToDisabledWithoutExplicitConfiguration() {
        var disabled = new AssayEntryExecutionService(archive, confirmations, previews, audit, codec, writer, json);
        assertThatThrownBy(() -> disabled.confirm(7, "s", ref)).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1059);
        verifyNoInteractions(archive, writer);
    }

    @Test
    void successfulResultRemainsReadableAfterConfirmationExpiry() {
        var token = token(service.confirm(7, "s", ref));
        service.execute(7, "s", ref, token);
        // Expiring the preview does not change the immutable confirmation credential binding.
        preview.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        assertThat(service.execute(7, "s", ref, token).replayed()).isTrue();
        assertThat(service.result(7, "s", ref).saved()).isTrue();
        verify(writer, times(1)).appendVerified(input, 7, null, judgement);
    }

    @Test
    void inconsistentTerminalStateIsNotPresentedAsUnsaved() {
        var token = token(service.confirm(7, "s", ref)); service.execute(7, "s", ref, token);
        preview.setStatus("CONFIRMED");
        assertThatThrownBy(() -> service.result(7, "s", ref)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void retainsQualityWarningsInSavedResult() {
        preview.setPublicPayloadJson("{\"warnings\":[\"尚未保存\",\"缺少检测值\"]}");
        var token = token(service.confirm(7, "s", ref));
        var result = service.execute(7, "s", ref, token);
        assertThat(result.details().path("warnings").toString()).contains("化验记录已保存", "缺少检测值").doesNotContain("尚未保存");
    }

    private static AssayEntryExecutionTokenDTO token(AssayEntryConfirmationVO confirmation) {
        var token = new AssayEntryExecutionTokenDTO(); token.setExecutionToken(confirmation.executionToken()); token.setIdempotencyKey(confirmation.idempotencyKey()); return token;
    }

    @Test void recoveryClosesConfirmedOperationBeforeLateExecution() {
        var token = token(service.confirm(7, "s", ref));
        assertThat(service.recover(7, "s", ref).statusLabel()).isEqualTo("已取消");
        assertThat(service.recover(7, "s", ref).saved()).isFalse();
        assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writer);
        verify(audit, times(1)).append(7, "s", ref, "RECOVERY_CANCELED", 200, null);
    }

    @Test void recoveryReadsSavedResultWithoutNeedingActiveSession() {
        var token = token(service.confirm(7, "s", ref));
        service.execute(7, "s", ref, token);
        when(archive.loadOwnedControl(7, "s", ref)).thenThrow(new BusinessException(1057, "会话已失效"));
        assertThat(service.recover(7, "s", ref).assayId()).isEqualTo(123);
        verify(writer, times(1)).appendVerified(input, 7, null, judgement);
    }

    @Test void recoveryAccessFailureDoesNotRevealOrChangeState() {
        when(archive.loadOwnedRecovery(7, "s", ref)).thenThrow(new BusinessException(1056, "权限已撤销"));
        assertThatThrownBy(() -> service.recover(7, "s", ref)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(confirmations, previews, audit, writer);
    }

    private void groupPreview() {
        var single = archive.verifyForExecution(7, "s", ref);
        var secondInput = new AssaySubmitDTO(); secondInput.setProductId(85); secondInput.setSampleDate(input.getSampleDate());
        var product = new Product(); product.setId(85); product.setProductName("第二产品");
        var entries = java.util.List.of(single.entries().get(0), new AssayEntryArchiveService.VerifiedEntry(secondInput,
                new AssayEntryStateService.State(product, 3, judgement, "{}")));
        when(archive.verifyForExecution(7, "s", ref)).thenReturn(new AssayEntryArchiveService.VerifiedPreview(preview, entries, 9, "测试组"));
        preview.setPublicPayloadJson("{\"entryKind\":\"GROUP_V1\",\"groupId\":9,\"productCount\":2,\"products\":["
                + "{\"productId\":84,\"sampleDate\":\"2026-09-24\",\"proposedVersion\":1,\"warnings\":[\"尚未保存\",\"质量提示\"]},"
                + "{\"productId\":85,\"sampleDate\":\"2026-09-24\",\"proposedVersion\":4,\"warnings\":[\"尚未保存\"]}]}");
        var second = new Assay(); second.setId(124); second.setVersion(4); second.setSampleDate(input.getSampleDate()); second.setCreatedAt(LocalDateTime.now());
        when(writer.appendVerified(secondInput, 7, 3, judgement)).thenReturn(second);
    }

    @Test void groupExecutionReplayAndRecoveryRetainEveryActualRecord() {
        groupPreview(); var token = token(service.confirm(7, "s", ref));
        var first = service.execute(7, "s", ref, token);
        assertThat(first.saved()).isTrue(); assertThat(first.assayId()).isNull(); assertThat(first.version()).isNull();
        assertThat(first.details().at("/products/0/assayId").asInt()).isEqualTo(123);
        assertThat(first.details().at("/products/1/assayId").asInt()).isEqualTo(124);
        assertThat(first.details().at("/products/1/recordVersion").asInt()).isEqualTo(4);
        assertThat(first.details().at("/products/0/warnings").toString()).contains("质量提示").doesNotContain("尚未保存");
        assertThat(service.execute(7, "s", ref, token).details()).isEqualTo(first.details());
        assertThat(service.recover(7, "s", ref).details()).isEqualTo(first.details());
        verify(writer, times(2)).appendVerified(any(), eq(7), any(), any());
        verify(audit).append(7, "s", ref, "EXECUTION_SUCCEEDED", 200, 123);
        verify(audit).append(7, "s", ref, "EXECUTION_SUCCEEDED", 200, 124);
        verify(audit).append(7, "s", ref, "GROUP_EXECUTION_SUCCEEDED", 200, null);
    }

    @Test void groupResultCannotOmitOrDuplicateAnActualRecord() throws Exception {
        groupPreview(); var token = token(service.confirm(7, "s", ref)); service.execute(7, "s", ref, token);
        String complete = stored.getResultJson();
        var missing = json.readTree(complete); ((com.fasterxml.jackson.databind.node.ArrayNode) missing.at("/details/products")).remove(1);
        stored.setResultJson(json.writeValueAsString(missing));
        assertThatThrownBy(() -> service.result(7, "s", ref)).isInstanceOf(IllegalStateException.class);
        var duplicate = json.readTree(complete); ((com.fasterxml.jackson.databind.node.ObjectNode) duplicate.at("/details/products/1")).put("assayId", 123);
        stored.setResultJson(json.writeValueAsString(duplicate));
        assertThatThrownBy(() -> service.recover(7, "s", ref)).isInstanceOf(IllegalStateException.class);
    }

    @Test void groupChangedStateAndMissingPublicMemberDoNotWrite() {
        groupPreview(); var token = token(service.confirm(7, "s", ref));
        preview.setPublicPayloadJson("{\"entryKind\":\"GROUP_V1\",\"groupId\":9,\"productCount\":0,\"products\":[]}");
        assertThatThrownBy(() -> service.execute(7, "s", ref, token)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(writer);
    }
}
