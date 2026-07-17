package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InventoryLedgerAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PreparePoolBalanceAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.InventoryLedgerAgentVO;
import com.Laibin.SugarInventory.domain.vo.PreparePoolBalanceAgentVO;
import com.Laibin.SugarInventory.service.InventoryAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/agent-read")
@RequiredArgsConstructor
public class InventoryAgentReadController {
    private final InventoryAgentReadService service;

    @PostMapping("/ledger/query")
    @PreAuthorize("hasAuthority('inventory:view')")
    public Result<InventoryLedgerAgentVO> queryInventoryLedger(@RequestBody(required = false) InventoryLedgerAgentQueryDTO query) {
        return Result.success(service.queryInventoryLedger(query));
    }

    @PostMapping("/prepare-pool-balance/query")
    @PreAuthorize("hasAuthority('inventory:view')")
    public Result<PreparePoolBalanceAgentVO> queryPreparePoolBalance(@RequestBody(required = false) PreparePoolBalanceAgentQueryDTO query) {
        return Result.success(service.queryPreparePoolBalance(query));
    }
}
