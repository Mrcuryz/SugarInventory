package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.DefaultFinishInboundExecutionDomainAdapter;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishInboundExecutionDomainAdapterTest {

    @Test
    void reconstructsOnlyTheImmutableServerInputAndReusesExistingBatchTransaction() {
        PalletCodeService palletCodeService = mock(PalletCodeService.class);
        DefaultFinishInboundExecutionDomainAdapter adapter =
                new DefaultFinishInboundExecutionDomainAdapter(
                        palletCodeService, new ObjectMapper().findAndRegisterModules());
        AgentFinishInboundExecutionPreview preview = new AgentFinishInboundExecutionPreview();
        preview.setNormalizedInputJson("""
                [{"code":"bt0014lu","warehouseName":"2","entryDate":"2026-08-10",
                  "side":"左","quantity":1,"unit":"0","remark":"受控确认"}]
                """);
        User user = new User();
        user.setId(7);
        when(palletCodeService.confirmFinishedTaskInBatch(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7)))
                .thenReturn(List.of(InVO.createDefault()));

        var result = adapter.execute(preview, user);

        assertThat(result.resultCode()).isEqualTo("FINISH_INBOUND_COMPLETED");
        assertThat(result.affectedPalletCount()).isEqualTo(1);
        assertThat(result.palletCodes()).containsExactly("BT0014LU");
        ArgumentCaptor<com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO> captor =
                ArgumentCaptor.forClass(
                        com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO.class);
        verify(palletCodeService).confirmFinishedTaskInBatch(captor.capture(),
                org.mockito.ArgumentMatchers.eq(7));
        var item = captor.getValue().getItems().get(0);
        assertThat(item.getWarehouseName()).isEqualTo("2");
        assertThat(item.getRowNumber()).isNull();
        assertThat(item.getLayer()).isNull();
    }
}
