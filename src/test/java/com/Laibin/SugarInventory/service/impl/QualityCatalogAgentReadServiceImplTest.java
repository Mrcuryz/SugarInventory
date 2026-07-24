package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.QualityCatalogAgentQueries;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.ProductQualityStandardRelationVO;
import com.Laibin.SugarInventory.service.AssayGroupService;
import com.Laibin.SugarInventory.service.ProductQualityStandardRelationService;
import com.Laibin.SugarInventory.service.ProductService;
import com.Laibin.SugarInventory.service.QualityStandardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QualityCatalogAgentReadServiceImplTest {
    private AssayGroupService assayGroupService;
    private ProductService productService;
    private ProductQualityStandardRelationService relationService;
    private QualityCatalogAgentReadServiceImpl service;

    @BeforeEach
    void setUp() {
        assayGroupService = mock(AssayGroupService.class);
        productService = mock(ProductService.class);
        relationService = mock(ProductQualityStandardRelationService.class);
        service = new QualityCatalogAgentReadServiceImpl(
                assayGroupService,
                mock(QualityStandardService.class),
                productService,
                relationService);
    }

    @Test
    void aggregatesExactProductStandardsAndAssayGroupsWithoutInternalIds() {
        Product product = new Product();
        product.setId(84);
        product.setProductName("黄冰糖（袋）");
        product.setProductType("黄冰糖");
        product.setStatus("成品");
        product.setPackagingMethod("袋");
        product.setWeightPerPiece(new BigDecimal("25.0"));
        product.setPiecesPerPallet(40);

        ProductQualityStandardRelationVO standard = new ProductQualityStandardRelationVO();
        standard.setStandardCode("YBT");
        standard.setStandardName("黄冰糖 v1");
        standard.setStandardVersion(1);
        standard.setStandardStatus("ENABLED");
        standard.setIsDefault(true);
        standard.setPriority(1);
        standard.setEnabled(true);

        AssayGroup assayGroup = new AssayGroup();
        assayGroup.setId(9);
        assayGroup.setStandardName("黄冰糖批量化验组");
        assayGroup.setRelatedProducts("84,85");
        assayGroup.setRemark("生产日批量录入");

        when(productService.getById(84)).thenReturn(product);
        when(relationService.listByProductId(84)).thenReturn(List.of(standard));
        when(assayGroupService.listByProductId(84)).thenReturn(List.of(assayGroup));

        QualityCatalogAgentQueries.ProductQualityConfiguration query =
                new QualityCatalogAgentQueries.ProductQualityConfiguration();
        query.setProductId(84);
        var result = service.queryProductQualityConfiguration(query);

        assertThat(result.getDataScope()).isEqualTo("CURRENT_PRODUCT_QUALITY_CONFIGURATION");
        assertThat(result.getProductName()).isEqualTo("黄冰糖（袋）");
        assertThat(result.getStandardCount()).isEqualTo(1);
        assertThat(result.getStandards()).singleElement().satisfies(item -> {
            assertThat(item.getStandardName()).isEqualTo("黄冰糖 v1");
            assertThat(item.getIsDefault()).isTrue();
        });
        assertThat(result.getAssayGroupCount()).isEqualTo(1);
        assertThat(result.getAssayGroups()).singleElement().satisfies(item ->
                assertThat(item.getGroupName()).isEqualTo("黄冰糖批量化验组"));
        assertThat(result.toString()).doesNotContain("productId", "qualityStandardId", "assayGroupId");
    }

    @Test
    void returnsAnAvailableConfigurationFactWhenNoRelationsAreConfigured() {
        Product product = new Product();
        product.setId(84);
        product.setProductName("黄冰糖（袋）");
        when(productService.getById(84)).thenReturn(product);
        when(relationService.listByProductId(84)).thenReturn(List.of());
        when(assayGroupService.listByProductId(84)).thenReturn(List.of());

        QualityCatalogAgentQueries.ProductQualityConfiguration query =
                new QualityCatalogAgentQueries.ProductQualityConfiguration();
        query.setProductId(84);
        var result = service.queryProductQualityConfiguration(query);

        assertThat(result.getProductName()).isEqualTo("黄冰糖（袋）");
        assertThat(result.getStandardCount()).isZero();
        assertThat(result.getStandards()).isEmpty();
        assertThat(result.getAssayGroupCount()).isZero();
        assertThat(result.getAssayGroups()).isEmpty();
    }

    @Test
    void rejectsMissingOrUnknownProduct() {
        assertThatThrownBy(() -> service.queryProductQualityConfiguration(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("productId");

        QualityCatalogAgentQueries.ProductQualityConfiguration query =
                new QualityCatalogAgentQueries.ProductQualityConfiguration();
        query.setProductId(999);
        when(productService.getById(999)).thenReturn(null);

        assertThatThrownBy(() -> service.queryProductQualityConfiguration(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到指定产品");
    }
}
