package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.WarehouseCapacityDistributionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseCapacityDistributionVO;
import com.Laibin.SugarInventory.service.WarehouseAgentReadService;
import com.Laibin.SugarInventory.domain.dto.WarehouseRecentOperationsAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseRecentOperationsAgentVO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMixedStorageFactsQueryDTO;
import com.Laibin.SugarInventory.domain.vo.WarehouseMixedStorageFactsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouse/agent-read")
@RequiredArgsConstructor
public class WarehouseAgentReadController {
    private final WarehouseAgentReadService service;

    @PostMapping("/capacity-distribution/query")
    @PreAuthorize("hasAuthority('warehouse:view')")
    public Result<WarehouseCapacityDistributionVO> queryCapacityDistribution(
            @RequestBody WarehouseCapacityDistributionQueryDTO query) {
        return Result.success(service.queryCapacityDistribution(query));
    }

    @PostMapping("/recent-operations/query")
    @PreAuthorize("hasAuthority('warehouse:view')")
    public Result<WarehouseRecentOperationsAgentVO> queryRecentOperations(
            @RequestBody WarehouseRecentOperationsAgentQueryDTO query) {
        return Result.success(service.queryRecentOperations(query));
    }

    @PostMapping("/mixed-storage-facts/query")
    @PreAuthorize("hasAuthority('warehouse:view')")
    public Result<WarehouseMixedStorageFactsVO> queryMixedStorageFacts(
            @RequestBody WarehouseMixedStorageFactsQueryDTO query) {
        return Result.success(service.queryMixedStorageFacts(query));
    }
}
