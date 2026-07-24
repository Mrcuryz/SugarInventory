package com.Laibin.SugarInventory.production.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.production.domain.dto.ProductionEntityResolveQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchListQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionOrderProgressQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialPickTraceQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionLabelCompletionQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionInProcessMaterialsAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionMaterialCandidatesAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionEntityResolutionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchListVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionOrderProgressVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialPickTraceVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionLabelCompletionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionInProcessMaterialsVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionMaterialCandidatesVO;
import com.Laibin.SugarInventory.production.service.ProductionAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/agent-read")
@RequiredArgsConstructor
public class ProductionAgentReadController {
    private final ProductionAgentReadService service;

    @PostMapping("/entities/resolve")
    @PreAuthorize("hasAnyAuthority('production:order:view','production:boiling:view')")
    public Result<ProductionEntityResolutionVO> resolveEntities(
            @RequestBody ProductionEntityResolveQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.resolveEntities(query, loginUser.getUser().getId()));
    }

    @PostMapping("/orders/progress/query")
    @PreAuthorize("hasAuthority('production:order:view')")
    public Result<ProductionOrderProgressVO> queryOrderProgress(
            @RequestBody ProductionOrderProgressQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryOrderProgress(query, loginUser.getUser().getId()));
    }

    @PostMapping("/boiling-batches/trace/query")
    @PreAuthorize("hasAuthority('production:boiling:view')")
    public Result<ProductionBoilingBatchTraceVO> queryBoilingBatchTrace(
            @RequestBody ProductionBoilingBatchTraceQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryBoilingBatchTrace(query, loginUser.getUser().getId()));
    }

    @PostMapping("/boiling-batches/query")
    @PreAuthorize("hasAuthority('production:boiling:view')")
    public Result<ProductionBoilingBatchListVO> queryBoilingBatches(
            @RequestBody ProductionBoilingBatchListQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryBoilingBatches(query, loginUser.getUser().getId()));
    }

    @PostMapping("/orders/material-pick-trace/query")
    @PreAuthorize("hasAuthority('production:material:view')")
    public Result<ProductionMaterialPickTraceVO> queryMaterialPickTrace(
            @RequestBody ProductionMaterialPickTraceQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryMaterialPickTrace(query, loginUser.getUser().getId()));
    }

    @PostMapping("/orders/label-completion/query")
    @PreAuthorize("hasAuthority('production:order:view')")
    public Result<ProductionLabelCompletionVO> queryProductionLabelCompletion(
            @RequestBody ProductionLabelCompletionQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryProductionLabelCompletion(query, loginUser.getUser().getId()));
    }

    @PostMapping("/materials/in-process/query")
    @PreAuthorize("hasAuthority('production:material:view')")
    public Result<ProductionInProcessMaterialsVO> queryInProcessMaterials(
            @RequestBody ProductionInProcessMaterialsAgentQueryDTO query) {
        return Result.success(service.queryInProcessMaterials(query));
    }

    @PostMapping("/orders/material-candidates/query")
    @PreAuthorize("hasAuthority('production:material:view')")
    public Result<ProductionMaterialCandidatesVO> queryMaterialCandidates(
            @RequestBody ProductionMaterialCandidatesAgentQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(service.queryMaterialCandidates(query, loginUser.getUser().getId()));
    }
}
