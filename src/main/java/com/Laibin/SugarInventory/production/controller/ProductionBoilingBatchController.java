package com.Laibin.SugarInventory.production.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionBoilingBatchSaveDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchTraceNodeVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionBoilingBatchVO;
import com.Laibin.SugarInventory.production.service.ProductionBoilingBatchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/boiling-batches")
@RequiredArgsConstructor
@Tag(name = "煮糖批次", description = "生产订单上游煮糖批次与余量追溯")
public class ProductionBoilingBatchController {
    private final ProductionBoilingBatchService boilingBatchService;

    @GetMapping
    @PreAuthorize("hasAuthority('production:boiling:view')")
    public Result<PageResult<ProductionBoilingBatchVO>> pageBatches(ProductionBoilingBatchQueryDTO query) {
        return Result.success(boilingBatchService.pageBatches(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('production:boiling:view')")
    public Result<ProductionBoilingBatchVO> getDetail(@PathVariable Long id) {
        return Result.success(boilingBatchService.getDetail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('production:boiling:create')")
    @LogOperation(value = "production_boiling_batch", type = OperationType.INSERT)
    public Result<ProductionBoilingBatchVO> createBatch(@RequestBody @Valid ProductionBoilingBatchSaveDTO dto,
                                                        @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(boilingBatchService.createBatch(dto,
                loginUser.getUser().getId(),
                loginUser.getUser().getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('production:boiling:update')")
    @LogOperation(value = "production_boiling_batch", type = OperationType.UPDATE)
    public Result<ProductionBoilingBatchVO> updateBatch(@PathVariable Long id,
                                                        @RequestBody @Valid ProductionBoilingBatchSaveDTO dto) {
        return Result.success(boilingBatchService.updateBatch(id, dto));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('production:boiling:cancel')")
    @LogOperation(value = "production_boiling_batch", type = OperationType.UPDATE)
    public Result<Void> cancelBatch(@PathVariable Long id,
                                    @AuthenticationPrincipal LoginUser loginUser) {
        boilingBatchService.cancelBatch(id, loginUser.getUser().getId());
        return Result.success(null);
    }

    @GetMapping("/{id}/trace")
    @PreAuthorize("hasAuthority('production:boiling:view')")
    public Result<ProductionBoilingBatchTraceNodeVO> getTrace(@PathVariable Long id) {
        return Result.success(boilingBatchService.getTrace(id));
    }

    @GetMapping("/{id}/graph")
    @PreAuthorize("hasAuthority('production:boiling:view')")
    public Result<ProductionBoilingBatchTraceNodeVO> getTraceGraph(@PathVariable Long id) {
        return Result.success(boilingBatchService.getTrace(id));
    }
}
