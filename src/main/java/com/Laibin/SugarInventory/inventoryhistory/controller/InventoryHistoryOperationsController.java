package com.Laibin.SugarInventory.inventoryhistory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.inventoryhistory.domain.vo.InventoryHistoryOperationsStatusVO;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryOperationsReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory-history/operations")
@RequiredArgsConstructor
public class InventoryHistoryOperationsController {
    private final InventoryHistoryOperationsReadService readService;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('log:view')")
    public Result<InventoryHistoryOperationsStatusVO> latestStatus() {
        return Result.success(readService.latestStatus());
    }
}
