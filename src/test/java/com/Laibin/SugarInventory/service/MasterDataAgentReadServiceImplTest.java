package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.ProductCatalogAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.ProductDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.service.impl.MasterDataAgentReadServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDataAgentReadServiceImplTest {
    @Test
    void catalogOmitsInternalIdsAndResolvesScreenMeshName() {
        ProductService products = mock(ProductService.class); ScreenMeshService meshes = mock(ScreenMeshService.class);
        Product product = product(); ScreenMesh mesh = new ScreenMesh(); mesh.setId(3); mesh.setMeshName("8目");
        when(products.getProductsByCondition(null, null, null)).thenReturn(List.of(product));
        when(meshes.findAllScreenMesh()).thenReturn(List.of(mesh));

        var result = new MasterDataAgentReadServiceImpl(products, meshes).queryProductCatalog(new ProductCatalogAgentQueryDTO());

        assertThat(result.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getProductName()).isEqualTo("单晶冰糖"); assertThat(item.getScreenMeshName()).isEqualTo("8目");
        });
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不包含内部产品/筛网 ID"));
    }

    @Test
    void detailRequiresExactUniqueNameAndOnlyRestatesConfiguredConversion() {
        ProductService products = mock(ProductService.class); ScreenMeshService meshes = mock(ScreenMeshService.class);
        when(products.getProductsByCondition("单晶冰糖", null, null)).thenReturn(List.of(product()));
        when(meshes.findAllScreenMesh()).thenReturn(List.of());
        ProductDetailAgentQueryDTO query = new ProductDetailAgentQueryDTO(); query.setProductName("单晶冰糖");

        var result = new MasterDataAgentReadServiceImpl(products, meshes).getProductDetail(query);

        assertThat(result.getConversionSummary()).isEqualTo("每托盘 40 件，每件 25 kg");
        assertThat(result.getLimitations()).anyMatch(value -> value.contains("不推导库存数量"));
    }

    private Product product() {
        Product product = new Product(); product.setId(9); product.setProductName("单晶冰糖");
        product.setProductType("白冰糖"); product.setStatus("成品"); product.setPackagingMethod("袋");
        product.setWeightPerPiece(new BigDecimal("25")); product.setPiecesPerPallet(40); product.setCanStack(true); product.setScreenMeshId(3);
        return product;
    }
}
