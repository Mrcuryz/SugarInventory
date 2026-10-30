package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentAssayEntryPreview;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayEntryPreviewVO;
import com.Laibin.SugarInventory.mapper.AgentAssayEntryPreviewMapper;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryArchiveServiceTest {
    private final AssayEntryAccessService access = mock(AssayEntryAccessService.class);
    private final AssayEntryStateService states = mock(AssayEntryStateService.class);
    private final AssayEntryPreviewService display = mock(AssayEntryPreviewService.class);
    private final AgentAssayEntryPreviewMapper mapper = mock(AgentAssayEntryPreviewMapper.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AssayEntryArchiveService service = new AssayEntryArchiveService(access, new AssayEntryInputValidator(), states, display, mapper, json, mock(AssayGroupEntryStateService.class));
    private AgentAssayEntryPreview saved;

    @BeforeEach
    void setup() throws Exception {
        Product product = new Product(); product.setId(84);
        when(states.lockAndRead(any())).thenReturn(new AssayEntryStateService.State(product, null, new AssayJudgeOutcome(), "{}"));
        when(states.json(any())).thenAnswer(call -> json.writeValueAsString(call.getArgument(0)));
        when(display.project(any(), any(), any(), any())).thenReturn(AssayEntryPreviewVO.builder().previewOnly(true)
                .productName("测试产品").warnings(List.of("旧提示", "产品未配置启用中的化验标准，不能据此认定合格。")).build());
        when(mapper.insert(any(AgentAssayEntryPreview.class))).thenAnswer(call -> {
            saved = call.getArgument(0); saved.setId(1L); return 1;
        });
        when(mapper.selectForUpdate(anyString())).thenAnswer(call -> saved);
        when(mapper.updateStatus(anyLong(), anyString())).thenAnswer(call -> { saved.setStatus(call.getArgument(1)); return 1; });
    }

    @Test
    void archivesExactInputAndFreshPublicPayloadAndSupersedesOlderPreviews() throws Exception {
        var result = service.create(7, "session-a", AssayEntryInputValidatorTest.valid());
        assertThat(result.previewRef()).matches("aep1_[A-Za-z0-9_-]{43}");
        assertThat(result.statusLabel()).isEqualTo("待确认");
        assertThat(result.preview().path("warnings").toString()).contains("尚未保存", "不能据此认定合格");
        assertThat(result.preview().path("previewOnly").asBoolean()).isTrue();
        assertThat(saved.getNormalizedInputJson()).contains("sampleDate", "colorValue");
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusMinutes(5));
        assertThat(service.load(7, "session-a", result.previewRef()).preview()).isEqualTo(result.preview());
        var order = inOrder(access, states, mapper);
        order.verify(access).requireCurrent(7, "session-a");
        order.verify(states).lockAndRead(any());
        order.verify(mapper).supersedeActive("session-a", 7);
        order.verify(mapper).insert(saved);
    }

    @Test
    void refusesOtherOwnerOrSessionEvenIfReferenceIsKnown() {
        var ref = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        assertThatThrownBy(() -> service.load(8, "session-a", ref)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.load(7, "session-b", ref)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsExpiredSupersededAndTamperedPreviews() {
        var ref = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        saved.setExpiresAt(saved.getCreatedAt().minusSeconds(1));
        assertThatThrownBy(() -> service.load(7, "session-a", ref)).isInstanceOf(BusinessException.class);
        String staleRef = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        saved.setStatus("SUPERSEDED");
        assertThatThrownBy(() -> service.load(7, "session-a", staleRef)).isInstanceOf(BusinessException.class);
    }

    @Test
    void modifiedMeasurementsOrExtendedExpiryInvalidateDigest() {
        var ref = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        saved.setNormalizedInputJson("{}");
        assertThatThrownBy(() -> service.load(7, "session-a", ref)).isInstanceOf(BusinessException.class);
        var secondRef = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        saved.setExpiresAt(saved.getExpiresAt().plusMinutes(1));
        assertThatThrownBy(() -> service.load(7, "session-a", secondRef)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cancellationIsIdempotentAndPreventsReuse() {
        var ref = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        service.cancel(7, "session-a", ref); service.cancel(7, "session-a", ref);
        verify(mapper, times(1)).updateStatus(1L, "CANCELED");
        assertThatThrownBy(() -> service.verifyUnchanged(7, "session-a", ref)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rechecksWholeStateBeforeConfirmation() {
        var ref = service.create(7, "session-a", AssayEntryInputValidatorTest.valid()).previewRef();
        assertThat(service.verifyUnchanged(7, "session-a", ref).digest()).isEqualTo(saved.getStateDigest());
        when(states.lockAndRead(any())).thenReturn(new AssayEntryStateService.State(new Product(), 2, new AssayJudgeOutcome(), "{\"changed\":true}"));
        assertThatThrownBy(() -> service.verifyUnchanged(7, "session-a", ref)).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
    }

    @Test
    void revokedPermissionStopsBeforeReadingOrReplacingPreview() {
        when(access.requireCurrent(7, "session-a")).thenThrow(new BusinessException(1056, "无权限"));
        assertThatThrownBy(() -> service.create(7, "session-a", AssayEntryInputValidatorTest.valid())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(states, display, mapper);
    }
}
