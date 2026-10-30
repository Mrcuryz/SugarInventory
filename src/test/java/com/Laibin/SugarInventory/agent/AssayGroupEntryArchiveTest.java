package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
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

class AssayGroupEntryArchiveTest {
    private final AssayEntryAccessService access = mock(AssayEntryAccessService.class);
    private final AssayEntryStateService states = mock(AssayEntryStateService.class);
    private final AssayGroupEntryStateService groups = mock(AssayGroupEntryStateService.class);
    private final AssayEntryPreviewService display = mock(AssayEntryPreviewService.class);
    private final AgentAssayEntryPreviewMapper mapper = mock(AgentAssayEntryPreviewMapper.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final AssayEntryArchiveService service = new AssayEntryArchiveService(access, new AssayEntryInputValidator(), states, display, mapper, json, groups);
    private AgentAssayEntryPreview row;
    private AssayGroupEntryStateService.GroupState group;

    @BeforeEach void setup() throws Exception {
        var request = AssayGroupEntryPreviewTest.request();
        var inputs = new AssayGroupEntryInputService(new AssayEntryInputValidator());
        var first = new AssayEntryInputValidator().normalize(inputs.inputFor(request, 2));
        var second = new AssayEntryInputValidator().normalize(inputs.inputFor(request, 3));
        var p2 = new Product(); p2.setId(2); p2.setProductName("甲产品");
        var p3 = new Product(); p3.setId(3); p3.setProductName("乙产品");
        group = new AssayGroupEntryStateService.GroupState(9, "测试组", List.of(first, second),
                List.of(new AssayEntryStateService.State(p2, null, new AssayJudgeOutcome(), "{}"),
                        new AssayEntryStateService.State(p3, 3, new AssayJudgeOutcome(), "{}")), "{\"members\":[2,3]}");
        when(groups.lockAndRead(any())).thenReturn(group);
        when(states.json(any())).thenAnswer(call -> json.writeValueAsString(call.getArgument(0)));
        when(display.project(any(), any(), any(), any())).thenAnswer(call -> {
            AssaySubmitDTO input = call.getArgument(0); Product product = call.getArgument(1); Integer version = call.getArgument(2);
            return AssayEntryPreviewVO.builder().productId(product.getId()).productName(product.getProductName())
                    .sampleDate(input.getSampleDate()).proposedVersion(version == null ? 1 : version + 1)
                    .previewOnly(true).warnings(List.of("旧提示", "质量提示")).build();
        });
        when(mapper.insert(any(AgentAssayEntryPreview.class))).thenAnswer(call -> { row = call.getArgument(0); row.setId(1L); return 1; });
        when(mapper.selectForUpdate(anyString())).thenAnswer(call -> row);
    }

    @Test void archivesEveryMemberWithExplicitKindAndIndependentVersions() throws Exception {
        var result = service.createGroup(7, "s", AssayGroupEntryPreviewTest.request());
        assertThat(result.preview().path("entryKind").asText()).isEqualTo("GROUP_V1");
        assertThat(result.preview().path("products").size()).isEqualTo(2);
        assertThat(result.preview().at("/products/0/proposedVersion").asInt()).isEqualTo(1);
        assertThat(result.preview().at("/products/1/proposedVersion").asInt()).isEqualTo(4);
        assertThat(result.preview().at("/products/1/warnings").toString()).contains("尚未保存", "质量提示");
        assertThat(json.readTree(row.getNormalizedInputJson()).path("entryKind").asText()).isEqualTo("GROUP_V1");
        var verified = service.verifyForExecution(7, "s", result.previewRef());
        assertThat(verified.isGroup()).isTrue(); assertThat(verified.entries()).hasSize(2);
        assertThatThrownBy(verified::input).isInstanceOf(BusinessException.class);
        assertThat(service.load(7, "s", result.previewRef()).preview()).isEqualTo(result.preview());
        verify(states, never()).lockAndRead(any());
    }

    @Test void membershipChangeRejectsOldArchiveBeforeAnyExecution() {
        var result = service.createGroup(7, "s", AssayGroupEntryPreviewTest.request());
        when(groups.lockAndRead(any())).thenReturn(new AssayGroupEntryStateService.GroupState(9, "测试组", group.inputs(), group.products(), "{\"members\":[2]}"));
        assertThatThrownBy(() -> service.verifyForExecution(7, "s", result.previewRef()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
    }

    @Test void authorizationPrecedesResolvingGroupAndCanceledPreviewCannotResume() {
        doThrow(new BusinessException(1056, "权限已撤销")).when(access).requireCurrent(8, "s");
        assertThatThrownBy(() -> service.createGroup(8, "s", AssayGroupEntryPreviewTest.request())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(groups, mapper);
        var result = service.createGroup(7, "s", AssayGroupEntryPreviewTest.request()); row.setStatus("CANCELED");
        assertThatThrownBy(() -> service.verifyForExecution(7, "s", result.previewRef())).isInstanceOf(BusinessException.class);
        verify(groups, times(1)).lockAndRead(any());
    }

    @Test void aGroupThatBecomesEmptyOrMissingRequiresNewPreviewRatherThanRetry() {
        var result = service.createGroup(7, "s", AssayGroupEntryPreviewTest.request());
        when(groups.lockAndRead(any())).thenThrow(new BusinessException(1051, "化验组没有产品"));
        assertThatThrownBy(() -> service.verifyForExecution(7, "s", result.previewRef()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
    }
}
