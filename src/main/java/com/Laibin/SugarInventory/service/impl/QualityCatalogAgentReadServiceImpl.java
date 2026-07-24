package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.QualityCatalogAgentQueries;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.*;
import com.Laibin.SugarInventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityCatalogAgentReadServiceImpl implements QualityCatalogAgentReadService {
    private final AssayGroupService assayGroupService;
    private final QualityStandardService qualityStandardService;
    private final ProductService productService;
    private final ProductQualityStandardRelationService relationService;

    @Override
    public QualityCatalogAgentVO.AssayGroups queryAssayGroups(QualityCatalogAgentQueries.AssayGroups query) {
        var source = query == null ? new QualityCatalogAgentQueries.AssayGroups() : query; int page = page(source.getPage()); int size = size(source.getSize());
        AssayGroupQueryDTO dto = new AssayGroupQueryDTO(); dto.setStandardName(text(source.getGroupName(), 100, "groupName")); dto.setPage(page); dto.setSize(size);
        var result = assayGroupService.queryAssays(dto); List<AssayGroupVO> rows = result.getRecords() == null ? List.of() : result.getRecords();
        return QualityCatalogAgentVO.AssayGroups.builder().dataScope("CURRENT_ASSAY_GROUP_CATALOG")
                .total(result.getTotal() == null ? 0 : result.getTotal()).page(page).size(size)
                .records(rows.stream().map(item -> QualityCatalogAgentVO.AssayGroupRow.builder().groupName(item.getStandardName())
                        .productNames(item.getRelatedProductList() == null ? List.of() : item.getRelatedProductList().stream().map(Product::getProductName).toList())
                        .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt()).remark(item.getRemark()).build()).toList())
                .limitations(List.of("化验组仅表示当前配置的产品分组，不是化验标准、合格结论或库存批次范围。", "结果不包含组 ID、产品 ID 或维护人，也不执行配置修改。"))
                .build();
    }

    @Override
    public QualityCatalogAgentVO.Standards queryStandards(QualityCatalogAgentQueries.Standards query) {
        var source = query == null ? new QualityCatalogAgentQueries.Standards() : query; int page = page(source.getPage()); int size = size(source.getSize());
        var result = qualityStandardService.pageQualityStandards(text(source.getProductType(), 50, "productType"), text(source.getStandardName(), 100, "standardName"), text(source.getStatus(), 30, "status"), page, size);
        List<QualityStandardVO> rows = result.getRecords() == null ? List.of() : result.getRecords();
        return QualityCatalogAgentVO.Standards.builder().dataScope("CURRENT_QUALITY_STANDARD_CATALOG")
                .total(result.getTotal() == null ? 0 : result.getTotal()).page(page).size(size)
                .records(rows.stream().map(this::standardRow).toList())
                .limitations(List.of("标准目录只表示当前配置及版本状态，不表示某次化验实际采用该标准。", "结果不执行标准或产品绑定修改。"))
                .build();
    }

    @Override
    public QualityCatalogAgentVO.StandardDetail getStandardDetail(QualityCatalogAgentQueries.StandardDetail query) {
        String code = query == null ? null : text(query.getStandardCode(), 100, "standardCode");
        if (code == null || query.getVersion() == null || query.getVersion() <= 0) throw new BusinessException(400, "standardCode 和正整数 version 均不能为空");
        List<QualityStandardVO> matches = qualityStandardService.listQualityStandards(null, null, null).stream()
                .filter(item -> code.equals(item.getStandardCode()) && query.getVersion().equals(item.getVersion())).toList();
        if (matches.isEmpty()) throw new BusinessException(404, "未找到指定代码和版本的质量标准");
        if (matches.size() > 1) throw new BusinessException(409, "指定代码和版本存在多个标准，无法安全选择");
        QualityStandardVO item = matches.getFirst();
        return QualityCatalogAgentVO.StandardDetail.builder().dataScope("CURRENT_QUALITY_STANDARD_DETAIL")
                .standardCode(item.getStandardCode()).standardName(item.getStandardName()).productType(item.getProductType())
                .standardLevel(item.getStandardLevel()).version(item.getVersion()).status(item.getStatus()).remark(item.getRemark())
                .metrics(item.getItems() == null ? List.of() : item.getItems().stream().map(metric -> QualityCatalogAgentVO.Metric.builder()
                        .metricCode(metric.getMetricCode()).metricName(metric.getMetricName()).minValue(metric.getMinValue())
                        .maxValue(metric.getMaxValue()).unit(metric.getUnit()).compareType(metric.getCompareType())
                        .sortOrder(metric.getSortOrder()).remark(metric.getRemark()).build()).toList())
                .relatedProductNames(item.getRelatedProducts() == null ? List.of() : item.getRelatedProducts().stream().map(QualityStandardRelatedProductVO::getProductName).toList())
                .limitations(List.of("详情是当前标准配置快照，不证明某份化验报告实际采用该版本。", "判定仍由确定性标准匹配与判定服务执行，LLM 不作最终质量结论。"))
                .build();
    }

    @Override
    public QualityCatalogAgentVO.ProductRelations queryProductRelations(QualityCatalogAgentQueries.ProductRelations query) {
        String name = query == null ? null : text(query.getProductName(), 100, "productName"); if (name == null) throw new BusinessException(400, "productName 不能为空");
        List<Product> matches = productService.getProductsByCondition(name, null, null).stream().filter(item -> name.equals(item.getProductName())).toList();
        if (matches.isEmpty()) throw new BusinessException(404, "未找到名称完全匹配的产品"); if (matches.size() > 1) throw new BusinessException(409, "产品名称存在多个完全匹配项");
        List<ProductQualityStandardRelationVO> rows = relationService.listByProductId(matches.getFirst().getId());
        rows = rows == null ? List.of() : rows;
        return QualityCatalogAgentVO.ProductRelations.builder().dataScope("CURRENT_PRODUCT_QUALITY_STANDARD_RELATIONS")
                .productName(name).count(rows.size()).records(rows.stream().map(item -> QualityCatalogAgentVO.Relation.builder()
                        .standardCode(item.getStandardCode()).standardName(item.getStandardName()).standardVersion(item.getStandardVersion())
                        .standardStatus(item.getStandardStatus()).isDefault(item.getIsDefault()).priority(item.getPriority()).enabled(item.getEnabled())
                        .effectiveFrom(item.getEffectiveFrom()).effectiveTo(item.getEffectiveTo()).remark(item.getRemark()).build()).toList())
                .limitations(List.of("绑定关系只表示当前配置和生效区间，不证明某次化验采用该标准或产品已合格。", "结果不包含关系、产品或标准内部 ID，也不执行绑定修改。"))
                .build();
    }

    @Override
    public QualityCatalogAgentVO.ProductQualityConfiguration queryProductQualityConfiguration(
            QualityCatalogAgentQueries.ProductQualityConfiguration query) {
        Integer productId = query == null ? null : query.getProductId();
        if (productId == null || productId <= 0) {
            throw new BusinessException(400, "productId 不能为空");
        }
        Product product = productService.getById(productId);
        if (product == null) {
            throw new BusinessException(404, "未找到指定产品");
        }

        List<ProductQualityStandardRelationVO> standardRows = relationService.listByProductId(productId);
        standardRows = standardRows == null ? List.of() : standardRows;
        List<QualityCatalogAgentVO.Relation> standards = standardRows.stream()
                .sorted(Comparator
                        .comparing((ProductQualityStandardRelationVO item) -> !Boolean.TRUE.equals(item.getIsDefault()))
                        .thenComparing(item -> item.getPriority() == null ? Integer.MAX_VALUE : item.getPriority()))
                .map(item -> QualityCatalogAgentVO.Relation.builder()
                        .standardCode(item.getStandardCode())
                        .standardName(item.getStandardName())
                        .standardVersion(item.getStandardVersion())
                        .standardStatus(item.getStandardStatus())
                        .isDefault(item.getIsDefault())
                        .priority(item.getPriority())
                        .enabled(item.getEnabled())
                        .effectiveFrom(item.getEffectiveFrom())
                        .effectiveTo(item.getEffectiveTo())
                        .remark(item.getRemark())
                        .build())
                .toList();

        List<QualityCatalogAgentVO.ProductAssayGroup> assayGroups = assayGroupService
                .listByProductId(productId)
                .stream()
                .map(item -> QualityCatalogAgentVO.ProductAssayGroup.builder()
                        .groupName(item.getStandardName())
                        .remark(item.getRemark())
                        .build())
                .toList();

        return QualityCatalogAgentVO.ProductQualityConfiguration.builder()
                .dataScope("CURRENT_PRODUCT_QUALITY_CONFIGURATION")
                .productName(product.getProductName())
                .productType(product.getProductType())
                .productStatus(product.getStatus())
                .packagingMethod(product.getPackagingMethod())
                .weightPerPiece(product.getWeightPerPiece())
                .piecesPerPallet(product.getPiecesPerPallet())
                .standardCount(standards.size())
                .standards(standards)
                .assayGroupCount(assayGroups.size())
                .assayGroups(assayGroups)
                .limitations(List.of(
                        "适用质量标准表示当前产品绑定配置，不证明某次化验实际采用该标准或产品已经合格。",
                        "所属批量化验组只表示当前批量化验分组，不是质量标准或库存批次范围。",
                        "结果不包含产品、标准、化验组或关系内部 ID，也不执行配置修改。"))
                .build();
    }

    private QualityCatalogAgentVO.StandardRow standardRow(QualityStandardVO item) { return QualityCatalogAgentVO.StandardRow.builder()
            .standardCode(item.getStandardCode()).standardName(item.getStandardName()).productType(item.getProductType()).standardLevel(item.getStandardLevel())
            .version(item.getVersion()).status(item.getStatus()).metricCount(item.getItems() == null ? 0 : item.getItems().size())
            .relatedProductCount(item.getRelatedProducts() == null ? 0 : item.getRelatedProducts().size()).remark(item.getRemark()).build(); }
    private int page(Integer value) { if (value == null) return 1; if (value < 1) throw new BusinessException(400, "page 必须大于 0"); return value; }
    private int size(Integer value) { int result = value == null ? 20 : value; if (result < 1 || result > 50) throw new BusinessException(400, "size 必须在 1 到 50 之间"); return result; }
    private String text(String value, int max, String field) { if (value == null || value.isBlank()) return null; String result = value.trim(); if (result.length() > max) throw new BusinessException(400, field + " 长度超限"); return result; }
}
