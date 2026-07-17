package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.ProductCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.vo.ProductCatalogAgentVO;
import com.Laibin.SugarInventory.domain.vo.ProductDetailAgentVO;
import com.Laibin.SugarInventory.domain.vo.ScreenMeshCatalogAgentVO;
import com.Laibin.SugarInventory.service.MasterDataAgentReadService;
import com.Laibin.SugarInventory.service.ProductService;
import com.Laibin.SugarInventory.service.ScreenMeshService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterDataAgentReadServiceImpl implements MasterDataAgentReadService {
    private final ProductService productService;
    private final ScreenMeshService screenMeshService;

    @Override
    public ProductCatalogAgentVO queryProductCatalog(ProductCatalogAgentQueryDTO query) {
        ProductCatalogAgentQueryDTO source = query == null ? new ProductCatalogAgentQueryDTO() : query;
        int page = page(source.getPage()); int size = size(source.getSize());
        String name = text(source.getProductName(), 100, "productName");
        String type = text(source.getProductType(), 50, "productType");
        String status = text(source.getProductStatus(), 50, "productStatus");
        String packaging = text(source.getPackagingMethod(), 50, "packagingMethod");
        String meshName = text(source.getScreenMeshName(), 100, "screenMeshName");
        Map<Integer, ScreenMesh> meshes = meshes();
        List<Product> products = safe(productService.getProductsByCondition(name, type, status)).stream()
                .filter(item -> packaging == null || packaging.equals(item.getPackagingMethod()))
                .filter(item -> meshName == null || meshName.equals(meshName(item, meshes))).toList();
        int from = Math.min((page - 1) * size, products.size()); int to = Math.min(from + size, products.size());
        return ProductCatalogAgentVO.builder().dataScope("CURRENT_PRODUCT_MASTER_DATA").total(products.size())
                .page(page).size(size).records(products.subList(from, to).stream().map(item -> row(item, meshes)).toList())
                .limitations(List.of("仅展示当前产品主数据，不表示当前库存、质量合格或可用于生产。",
                        "结果不包含内部产品/筛网 ID、创建人或更新人，也不执行产品配置修改。"))
                .build();
    }

    @Override
    public ProductDetailAgentVO getProductDetail(ProductDetailAgentQueryDTO query) {
        String name = query == null ? null : text(query.getProductName(), 100, "productName");
        if (name == null) throw new BusinessException(400, "productName 不能为空");
        List<Product> matches = safe(productService.getProductsByCondition(name, null, null)).stream()
                .filter(item -> name.equals(item.getProductName())).toList();
        if (matches.isEmpty()) throw new BusinessException(404, "未找到名称完全匹配的产品");
        if (matches.size() > 1) throw new BusinessException(409, "产品名称存在多个完全匹配项，无法安全选择");
        Product item = matches.getFirst(); String mesh = meshName(item, meshes());
        return ProductDetailAgentVO.builder().dataScope("CURRENT_PRODUCT_MASTER_DETAIL")
                .productName(item.getProductName()).productType(item.getProductType()).productStatus(item.getStatus())
                .packagingMethod(item.getPackagingMethod()).weightPerPiece(item.getWeightPerPiece())
                .piecesPerPallet(item.getPiecesPerPallet()).canStack(item.getCanStack()).screenMeshName(mesh)
                .conversionSummary(conversion(item))
                .limitations(List.of("换算摘要仅复述当前产品配置，不推导库存数量或装载建议。",
                        "详情不包含内部 ID、创建/更新账号，也不执行任何主数据变更。"))
                .build();
    }

    @Override
    public ScreenMeshCatalogAgentVO queryScreenMeshCatalog(ScreenMeshCatalogAgentQueryDTO query) {
        ScreenMeshCatalogAgentQueryDTO source = query == null ? new ScreenMeshCatalogAgentQueryDTO() : query;
        int page = page(source.getPage()); int size = size(source.getSize());
        String name = text(source.getMeshName(), 100, "meshName");
        List<ScreenMesh> rows = safe(screenMeshService.findScreenMeshes(name));
        int from = Math.min((page - 1) * size, rows.size()); int to = Math.min(from + size, rows.size());
        return ScreenMeshCatalogAgentVO.builder().dataScope("CURRENT_SCREEN_MESH_MASTER_DATA")
                .total(rows.size()).page(page).size(size)
                .records(rows.subList(from, to).stream().map(item -> ScreenMeshCatalogAgentVO.Row.builder()
                        .meshName(item.getMeshName()).description(item.getDescription()).build()).toList())
                .limitations(List.of("仅展示当前筛网目录配置，不表示产品当前实际使用情况。",
                        "结果不包含内部筛网 ID 或维护账号，也不执行筛网配置修改。"))
                .build();
    }

    private ProductCatalogAgentVO.Row row(Product item, Map<Integer, ScreenMesh> meshes) {
        return ProductCatalogAgentVO.Row.builder().productName(item.getProductName()).productType(item.getProductType())
                .productStatus(item.getStatus()).packagingMethod(item.getPackagingMethod()).weightPerPiece(item.getWeightPerPiece())
                .piecesPerPallet(item.getPiecesPerPallet()).canStack(item.getCanStack()).screenMeshName(meshName(item, meshes)).build();
    }
    private Map<Integer, ScreenMesh> meshes() {
        return safe(screenMeshService.findAllScreenMesh()).stream().filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ScreenMesh::getId, Function.identity(), (left, right) -> left));
    }
    private String meshName(Product product, Map<Integer, ScreenMesh> meshes) {
        ScreenMesh mesh = product.getScreenMeshId() == null ? null : meshes.get(product.getScreenMeshId());
        return mesh == null ? null : mesh.getMeshName();
    }
    private String conversion(Product item) {
        if (item.getPiecesPerPallet() == null || item.getWeightPerPiece() == null) return "换算配置不完整";
        return "每托盘 " + item.getPiecesPerPallet() + " 件，每件 " + item.getWeightPerPiece() + " kg";
    }
    private int page(Integer value) { if (value == null) return 1; if (value < 1) throw new BusinessException(400, "page 必须大于 0"); return value; }
    private int size(Integer value) { int result = value == null ? 20 : value; if (result < 1 || result > 50) throw new BusinessException(400, "size 必须在 1 到 50 之间"); return result; }
    private String text(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new BusinessException(400, field + " 长度超限"); return result; }
    private <T> List<T> safe(List<T> rows) { return rows == null ? List.of() : rows; }
}
