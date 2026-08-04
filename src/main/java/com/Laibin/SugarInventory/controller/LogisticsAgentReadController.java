package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.PalletTaskAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletTasksAgentVO;
import com.Laibin.SugarInventory.service.LogisticsAgentReadService;
import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.domain.dto.StockDocumentAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.StockDocumentsAgentVO;
import com.Laibin.SugarInventory.domain.dto.AutoInboundBatchesAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AutoInboundBatchDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchesAgentVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchDetailAgentVO;
import com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.TaskTransitionPreviewVO;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logistics/agent-read")
@RequiredArgsConstructor
public class LogisticsAgentReadController {
    private final LogisticsAgentReadService service;

    @PostMapping("/pallet-tasks/query")
    @PreAuthorize("hasAuthority('task:view')")
    public Result<PalletTasksAgentVO> queryPalletTasks(@RequestBody PalletTaskAgentQueryDTO query) {
        return Result.success(service.queryPalletTasks(query));
    }

    @PostMapping("/stock-documents/query")
    @PreAuthorize("hasAuthority('document:view')")
    public Result<StockDocumentsAgentVO> queryStockDocuments(
            @RequestBody StockDocumentAgentQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryStockDocuments(query, loginUser.getUser()));
    }

    @PostMapping("/auto-inbound/batches/query")
    @PreAuthorize("hasAuthority('task:view')")
    public Result<AutoInboundBatchesAgentVO> queryAutoInboundBatches(
            @RequestBody AutoInboundBatchesAgentQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryAutoInboundBatches(query, loginUser.getUser()));
    }

    @PostMapping("/auto-inbound/batches/detail/query")
    @PreAuthorize("hasAuthority('task:view')")
    public Result<AutoInboundBatchDetailAgentVO> getAutoInboundBatchDetail(
            @RequestBody AutoInboundBatchDetailAgentQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.getAutoInboundBatchDetail(query, loginUser.getUser()));
    }

    @PostMapping("/pallet-tasks/transition/preview")
    @PreAuthorize("hasAuthority('task:view') and hasAuthority('task:confirm')")
    public Result<TaskTransitionPreviewVO> previewTaskTransition(
            @RequestBody @Valid TaskTransitionPreviewDTO request,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.previewTaskTransition(request, loginUser.getUser()));
    }
}
