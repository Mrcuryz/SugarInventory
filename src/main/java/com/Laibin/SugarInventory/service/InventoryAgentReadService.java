package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.InventoryLedgerAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PreparePoolBalanceAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.InventoryLedgerAgentVO;
import com.Laibin.SugarInventory.domain.vo.PreparePoolBalanceAgentVO;

public interface InventoryAgentReadService {
    InventoryLedgerAgentVO queryInventoryLedger(InventoryLedgerAgentQueryDTO query);
    PreparePoolBalanceAgentVO queryPreparePoolBalance(PreparePoolBalanceAgentQueryDTO query);
}
