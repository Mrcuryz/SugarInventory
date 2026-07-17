package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.ProductUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryMapper inventoryMapper;

    @InjectMocks
    private ProductServiceImpl service;

    @Test
    void rejectsWeightChangeWhenProductHasCurrentInventory() {
        Product product = product();
        ProductUpdateDTO dto = updateDto();
        dto.setWeightPerPiece(new BigDecimal("41.00"));
        when(productMapper.selectById(1)).thenReturn(product);
        when(inventoryMapper.existsByProductId(1)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProduct(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PRODUCT_SPECIFICATION_LOCKED_BY_INVENTORY.getMessage())
                .extracting("code")
                .isEqualTo(ErrorCode.PRODUCT_SPECIFICATION_LOCKED_BY_INVENTORY.getCode());
    }

    @Test
    void rejectsPalletSizeChangeWhenProductHasCurrentInventory() {
        ProductUpdateDTO dto = updateDto();
        dto.setPiecesPerPallet(30);
        when(productMapper.selectById(1)).thenReturn(product());
        when(inventoryMapper.existsByProductId(1)).thenReturn(true);

        assertThatThrownBy(() -> service.updateProduct(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.PRODUCT_SPECIFICATION_LOCKED_BY_INVENTORY.getMessage());
    }

    @Test
    void sameNumericSpecificationDoesNotRequireEmptyInventory() {
        Product product = product();
        ProductUpdateDTO dto = updateDto();
        dto.setProductName("黄冰糖（新名称）");
        dto.setWeightPerPiece(new BigDecimal("40.00"));
        dto.setPiecesPerPallet(25);
        when(productMapper.selectById(1)).thenReturn(product);
        stubSuccessfulUpdate(false);

        service.updateProduct(dto, 7);

        verify(inventoryMapper, never()).existsByProductId(1);
        verifyUpdateRequiresNoInventory(false);
    }

    @Test
    void specificationChangeUsesAtomicNoInventoryCondition() {
        ProductUpdateDTO dto = updateDto();
        dto.setPiecesPerPallet(30);
        when(productMapper.selectById(1)).thenReturn(product());
        when(inventoryMapper.existsByProductId(1)).thenReturn(false);
        stubSuccessfulUpdate(true);

        service.updateProduct(dto, 7);

        verify(inventoryMapper).existsByProductId(1);
        verifyUpdateRequiresNoInventory(true);
    }

    private void stubSuccessfulUpdate(boolean requireNoInventory) {
        when(productMapper.dynamicUpdate(
                anyInt(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(),
                eq(requireNoInventory))).thenReturn(1);
    }

    private void verifyUpdateRequiresNoInventory(boolean expected) {
        verify(productMapper).dynamicUpdate(
                anyInt(), any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(),
                eq(expected));
    }

    private Product product() {
        Product product = new Product();
        product.setId(1);
        product.setProductName("黄冰糖");
        product.setWeightPerPiece(new BigDecimal("40"));
        product.setPiecesPerPallet(25);
        return product;
    }

    private ProductUpdateDTO updateDto() {
        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setProductId(1);
        return dto;
    }
}
