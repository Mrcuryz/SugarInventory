package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InventoryLedgerAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PreparePoolBalanceAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.SemiPreparePoolBalanceVO;
import com.Laibin.SugarInventory.mapper.InventorySummaryMapper;
import com.Laibin.SugarInventory.mapper.model.InventoryLedgerAgentRow;
import com.Laibin.SugarInventory.service.impl.InventoryAgentReadServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryAgentReadServiceImplTest {
    @Test
    void inventoryLedgerIsExplicitlyCurrentSnapshotNotHistoryOrQualification() {
        InventorySummaryMapper mapper = mock(InventorySummaryMapper.class);
        InventoryLedgerAgentRow row = new InventoryLedgerAgentRow(); row.setWarehouseName("1号库"); row.setProductName("单晶冰糖"); row.setPalletQuantity(1); row.setPieces(20);
        when(mapper.selectInventoryLedger(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyLong(), anyInt())).thenReturn(List.of(row));
        when(mapper.countInventoryLedger(isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(1L);
        var service = new InventoryAgentReadServiceImpl(mapper, mock(InventoryService.class), mock(ScreenMeshService.class));

        var result = service.queryInventoryLedger(new InventoryLedgerAgentQueryDTO());

        assertThat(result.getDataScope()).isEqualTo("CURRENT_INVENTORY_LEDGER_ROWS");
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不是完整历史流水"));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不证明当前库存批次质量合格"));
    }

    @Test
    void preparePoolOnlySupportsPositiveCurrentBalance() {
        InventoryService inventory = mock(InventoryService.class); SemiPreparePoolBalanceVO row = new SemiPreparePoolBalanceVO(); row.setProductName("半成品糖"); row.setRemainingPieces(12);
        when(inventory.pagePreparePoolBalance(isNull(), isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt())).thenReturn(new PageResult<>(1L, List.of(row)));
        var service = new InventoryAgentReadServiceImpl(mock(InventorySummaryMapper.class), inventory, mock(ScreenMeshService.class));
        var result = service.queryPreparePoolBalance(new PreparePoolBalanceAgentQueryDTO());
        assertThat(result.getRecords()).singleElement().satisfies(item -> assertThat(item.getRemainingPieces()).isEqualTo(12));
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不等于已为某生产订单保留"));

        PreparePoolBalanceAgentQueryDTO unsupported = new PreparePoolBalanceAgentQueryDTO(); unsupported.setPositiveOnly(false);
        assertThatThrownBy(() -> service.queryPreparePoolBalance(unsupported)).hasMessageContaining("positiveOnly=true");
    }
}
