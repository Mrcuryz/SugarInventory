package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.ProductQualityStandardRelationDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.ProductQualityStandardRelationVO;
import com.Laibin.SugarInventory.service.ProductQualityStandardRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product-quality-standards")
@Tag(name = "产品与化验标准关联", description = "维护产品与化验标准的正式关系")
@PreAuthorize("hasAuthority('quality_standard:view')")
public class ProductQualityStandardRelationController {

    @Autowired
    private ProductQualityStandardRelationService relationService;

    @GetMapping("/products/{productId}")
    @Operation(summary = "查询产品的标准关联")
    public Result<List<ProductQualityStandardRelationVO>> listByProduct(@PathVariable Integer productId) {
        return Result.success(relationService.listByProductId(productId));
    }

    @PostMapping("/bind")
    @PreAuthorize("hasAuthority('quality_standard:bind_product')")
    @LogOperation(value = "产品标准关系", type = OperationType.INSERT)
    @Operation(summary = "绑定产品标准关系")
    public Result<ProductQualityStandardRelationVO> bindRelation(@RequestBody ProductQualityStandardRelationDTO dto,
                                                                 @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(relationService.bindRelation(dto, loginUser.getUser().getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('quality_standard:bind_product')")
    @LogOperation(value = "产品标准关系", type = OperationType.UPDATE)
    @Operation(summary = "更新产品标准关系")
    public Result<ProductQualityStandardRelationVO> updateRelation(@PathVariable Integer id,
                                                                   @RequestBody ProductQualityStandardRelationDTO dto,
                                                                   @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(relationService.updateRelation(id, dto, loginUser.getUser().getId()));
    }

    @PostMapping("/{id}/default")
    @PreAuthorize("hasAuthority('quality_standard:bind_product')")
    @LogOperation(value = "产品标准关系", type = OperationType.UPDATE)
    @Operation(summary = "设置默认标准")
    public Result<ProductQualityStandardRelationVO> setDefault(@PathVariable Integer id,
                                                               @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(relationService.setDefaultRelation(id, loginUser.getUser().getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('quality_standard:bind_product')")
    @LogOperation(value = "产品标准关系", type = OperationType.DELETE)
    @Operation(summary = "删除产品标准关系")
    public Result<Boolean> deleteRelation(@PathVariable Integer id) {
        relationService.deleteRelation(id);
        return Result.success(true);
    }
}
