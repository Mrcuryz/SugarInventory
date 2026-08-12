package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementActionContext;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.OutStockMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutStockServiceImplLegacyBoundaryTest {

    @Mock
    private InventoryMapper inventoryMapper;
    @Mock
    private WarehouseMapper warehouseMapper;
    @Mock
    private OutStockMapper outStockMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private AssayMapper assayMapper;
    @Mock
    private UserMapper userMapper;

    private OutStockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OutStockServiceImpl(
                inventoryMapper,
                warehouseMapper,
                outStockMapper,
                productMapper,
                assayMapper,
                userMapper
        );
        ReflectionTestUtils.setField(service, "stockMovementActionContext", new StockMovementActionContext());
    }

    @Test
    void stackOutboundUsesProductScopedLegacyInventoryOnly() {
        OutStockRequestDTO request = request();
        Product product = new Product();
        product.setId(request.getProductId());
        when(productMapper.selectById(request.getProductId())).thenReturn(product);
        when(inventoryMapper.getLegacyInventoryStackOrderForUpdate(
                request.getWarehouseId(), request.getProductId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.processStackOutStock(request, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("二维码托盘请通过托盘任务出库");

        verify(inventoryMapper).getLegacyInventoryStackOrderForUpdate(
                request.getWarehouseId(), request.getProductId());
    }

    @Test
    void regularOutboundRejectsWarehouseWithNoLegacyInventory() {
        OutStockRequestDTO request = request();
        Product product = new Product();
        product.setId(request.getProductId());
        when(productMapper.selectById(request.getProductId())).thenReturn(product);
        when(inventoryMapper.getLastLegacyInventoryForUpdate(
                request.getWarehouseId(), request.getProductId())).thenReturn(null);

        assertThatThrownBy(() -> service.processOutStock(request, 9))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("传统出库仅处理无二维码库存");

        verify(inventoryMapper).getLastLegacyInventoryForUpdate(
                request.getWarehouseId(), request.getProductId());
    }

    private OutStockRequestDTO request() {
        OutStockRequestDTO request = new OutStockRequestDTO();
        request.setProductId(11);
        request.setWarehouseId(22);
        request.setQuantity(1);
        request.setUnit("0");
        request.setOutType(0);
        return request;
    }
}
