package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.ProductCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.ProductCatalogAgentVO;
import com.Laibin.SugarInventory.domain.vo.ProductDetailAgentVO;
import com.Laibin.SugarInventory.domain.vo.ScreenMeshCatalogAgentVO;
import com.Laibin.SugarInventory.service.MasterDataAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/master-data/agent-read") @RequiredArgsConstructor
public class MasterDataAgentReadController {
    private final MasterDataAgentReadService service;
    @PostMapping("/products/query") @PreAuthorize("hasAuthority('product:view')")
    public Result<ProductCatalogAgentVO> queryProducts(@RequestBody ProductCatalogAgentQueryDTO query) { return Result.success(service.queryProductCatalog(query)); }
    @PostMapping("/products/detail/query") @PreAuthorize("hasAuthority('product:view')")
    public Result<ProductDetailAgentVO> getProductDetail(@RequestBody ProductDetailAgentQueryDTO query) { return Result.success(service.getProductDetail(query)); }
    @PostMapping("/screen-meshes/query") @PreAuthorize("hasAuthority('screen_mesh:view')")
    public Result<ScreenMeshCatalogAgentVO> queryScreenMeshes(@RequestBody ScreenMeshCatalogAgentQueryDTO query) { return Result.success(service.queryScreenMeshCatalog(query)); }
}
