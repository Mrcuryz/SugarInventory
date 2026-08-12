package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.assembler.ManagementViewAssembler;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.dto.ProductCreateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductInfoVO;
import com.Laibin.SugarInventory.domain.dto.ProductUpdateDTO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "产品管理", description = "产品管理相关接口，包括产品查询、创建、更新、删除等")
@PreAuthorize("hasAuthority('product:view')")
public class ProductController {
    private final ProductService productService;

    @Operation(summary = "查询产品信息", description = "根据产品ID查询产品信息，返回产品详细信息")
    @GetMapping("/{id}")
    public Result<ProductVO> getProduct(@PathVariable Integer id) {
        return Result.success(ManagementViewAssembler.toProductVO(productService.getById(id)));
    }


    /**
     * 根据名称查询产品信息
     *
     * @param name 产品名称
     * @return 产品列表
     */
    @Operation(summary = "根据名称查询产品信息", description = "根据产品名称支持模糊查询，返回产品列表")
    @GetMapping("/product")
    public Result<List<ProductVO>> getProductsByCondition(
            @Parameter(description = "产品名称，支持模糊查询", example = "冰", required = false)
            @RequestParam(required = false) String name,
            @Parameter(description = "产品类型，例如 '白冰糖' 或 '黄冰糖'", required = false)
            @RequestParam(required = false) String type,
            @Parameter(description = "产品状态，例如 '半成品' 或 '成品'", required = false)
            @RequestParam(required = false) String status
    ) {
        return Result.success(ManagementViewAssembler.toProductVOs(
                productService.getProductsByCondition(name, type, status)));
    }

    @Operation(summary = "分页查询产品信息", description = "根据产品名称、类型、状态分页查询")
    @GetMapping("/product/page")
    public Result<PageResult<ProductVO>> pageProductsByCondition(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10") Integer size
    ) {
        List<Product> list = productService.getProductsByCondition(name, type, status);
        if (list == null) {
            list = List.of();
        }
        int effectivePage = page == null || page < 1 ? 1 : page;
        int effectiveSize = size == null || size < 1 ? 10 : size;
        int fromIndex = Math.min((effectivePage - 1) * effectiveSize, list.size());
        int toIndex = Math.min(fromIndex + effectiveSize, list.size());
        PageResult<Product> result = new PageResult<>((long) list.size(), list.subList(fromIndex, toIndex));
        return Result.success(ManagementViewAssembler.toProductPage(result));
    }

    /**
     * 查询所有半成品名称
     *
     * @return 半成品名称列表
     */
    @Operation(summary = "查询所有半成品名称", description = "返回所有半成品的产品ID和产品名称列表")
    @GetMapping("/semi-product-names")
    public Result<List<ProductInfoVO>> getSemiProductNames() {
        return Result.success(productService.getSemiProductNames());
    }

    /**
     * 根据条件查询半成品名称
     *
     * @param name 半成品名称
     * @param type 半成品类型
     * @return 半成品名称列表
     */
    @Operation(summary = "根据条件查询半成品名称", description = "根据半成品名称和类型条件查询半成品列表")
    @GetMapping("/semi-products")
    public Result<List<ProductInfoVO>> getSemiProducts(
            @Parameter(description = "半成品名称，支持模糊查询", example = "冰", required = false)
            @RequestParam(required = false) String name,
            @Parameter(description = "半成品类型，例如 '白冰糖' 或 '黄冰糖'", required = false)
            @RequestParam(required = false) String type
    ) {
        return Result.success(productService.getSemiProductsByCondition(name, type));
    }

    /**
     * 查询所有成品名称
     *
     * @return 成品名称列表
     */
    @Operation(summary = "查询所有成品名称", description = "返回所有成品的产品ID和产品名称列表")
    @GetMapping("/finished-product-names")
    public Result<List<ProductInfoVO>> getFinishedProductNames() {
        return Result.success(productService.getFinishedProductNames());
    }

    /**
     * 根据条件查询成品名称
     *
     * @param name 成品名称
     * @param type 成品类型
     * @return 成品名称列表
     */
    @Operation(summary = "根据条件查询成品名称", description = "根据成品名称和类型条件查询成品列表")
    @GetMapping("/finished-products")
    public Result<List<ProductInfoVO>> getFinishedProducts(
            @Parameter(description = "成品名称，支持模糊查询", example = "冰糖", required = false)
            @RequestParam(required = false) String name,
            @Parameter(description = "成品类型，例如 '白冰糖' 或 '黄冰糖'", required = false)
            @RequestParam(required = false) String type
    ) {
        return Result.success(productService.getFinishedProductsByCondition(name, type));
    }


    /**
     * 创建产品
     *
     * @param vo        产品创建信息
     *                  参数：String productName;         产品名称（必填）
     *                  String productType;         产品类型（必填）
     *                  String status;              产品状态（必填）
     *                  String packagingMethod;     包装方式（选填）
     *                  BigDecimal weightPerPiece;  每份重量（必填）
     * @param loginUser 登录用户信息
     * @return 成功或失败信息
     */
    @Operation(summary = "创建产品", description = "根据产品创建信息创建新产品，返回新创建产品的详细信息")
    @LogOperation(value = "产品", type = OperationType.INSERT)
    @PostMapping("")
    @PreAuthorize("hasAuthority('product:create')")
    public Result<Boolean> createProduct(
            @Validated @RequestBody ProductCreateDTO vo,
            @Parameter(description = "当前登录用户，基于Token解析获得", required = true)
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        productService.createProduct(vo, loginUser.getUser());
        return Result.success(true);
    }

    /**
     * 根据条件更新产品信息接口
     *
     * @param dto 产品更新信息
     *            参数：Integer productId;          产品id（必填）
     *            String productName;         产品名称（选填）
     *            String productType;         产品类型（选填）
     *            String status;              产品状态（选填）
     *            String packagingMethod;     包装方式（选填）
     *            BigDecimal weightPerPiece;  每份重量（选填）
     *            Integer ScreenMeshId;       筛网ID（选填）
     *            权限注解 @PreAuthorize("hasAuthority('product:update')") 用于控制用户是否有权限进行更新操作
     *            注解 @AuthenticationPrincipal 用于获取当前登录用户信息
     * @return 成功或失败信息
     */
    @Operation(summary = "更新产品", description = "根据产品ID更新产品信息；产品存在当前库存时，禁止修改每板件数和单件重量，不同规格应新建产品")
    @LogOperation(value = "产品", type = OperationType.UPDATE)
    @PutMapping("")
    @PreAuthorize("hasAuthority('product:update')")
    public Result<ProductVO> updateProduct(
            @Validated @RequestBody ProductUpdateDTO dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return Result.success(ManagementViewAssembler.toProductVO(
                productService.updateProduct(dto, loginUser.getUser().getId())));
    }

    @Operation(summary = "删除产品", description = "根据产品ID删除产品")
    @LogOperation(value = "产品", type = OperationType.DELETE)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public Result<Void> deleteProduct(
            @Parameter(description = "产品ID", required = true)
            @PathVariable Integer id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        productService.deleteProduct(id, loginUser.getUser().getId());
        return Result.success(null);
    }


    @Operation(summary = "获取产品所有存放的库位", description = "获取产品所有存放的库位")
    @GetMapping("getProductWarehouse/{id}")
    public Result<List<VInventorySummary>> getProductWarehouse(
            @Parameter(description = "产品ID", required = true)
            @PathVariable Integer id
    ) {
        List<VInventorySummary> list = productService.getProductWarehouse(id);
        return Result.success(list);
    }
}
