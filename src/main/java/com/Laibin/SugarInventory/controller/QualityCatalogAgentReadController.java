package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.QualityCatalogAgentQueries;
import com.Laibin.SugarInventory.domain.vo.QualityCatalogAgentVO;
import com.Laibin.SugarInventory.service.QualityCatalogAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/quality/agent-read") @RequiredArgsConstructor
public class QualityCatalogAgentReadController {
    private final QualityCatalogAgentReadService service;
    @PostMapping("/assay-groups/query") @PreAuthorize("hasAuthority('assay:view')")
    public Result<QualityCatalogAgentVO.AssayGroups> groups(@RequestBody QualityCatalogAgentQueries.AssayGroups q) { return Result.success(service.queryAssayGroups(q)); }
    @PostMapping("/standards/query") @PreAuthorize("hasAuthority('quality_standard:view')")
    public Result<QualityCatalogAgentVO.Standards> standards(@RequestBody QualityCatalogAgentQueries.Standards q) { return Result.success(service.queryStandards(q)); }
    @PostMapping("/standards/detail/query") @PreAuthorize("hasAuthority('quality_standard:view')")
    public Result<QualityCatalogAgentVO.StandardDetail> detail(@RequestBody QualityCatalogAgentQueries.StandardDetail q) { return Result.success(service.getStandardDetail(q)); }
    @PostMapping("/product-standard-relations/query") @PreAuthorize("hasAuthority('quality_standard:view')")
    public Result<QualityCatalogAgentVO.ProductRelations> relations(@RequestBody QualityCatalogAgentQueries.ProductRelations q) { return Result.success(service.queryProductRelations(q)); }
}
