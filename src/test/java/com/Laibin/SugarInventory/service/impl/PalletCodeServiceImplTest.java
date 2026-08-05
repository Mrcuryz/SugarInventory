package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class PalletCodeServiceImplTest {

    @Test
    void parseAndFindDistinguishesInvalidCodeFromMissingPallet() {
        PalletCodeServiceImpl service = new PalletCodeServiceImpl();

        assertThatThrownBy(() -> service.parseAndFind("BT999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INVALID_PALLET_CODE.getCode());

        PalletCodeMapper mapper = mock(PalletCodeMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);

        assertThatThrownBy(() -> service.parseAndFind("BTZZZZZZ"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PALLET_CODE_NOT_FOUND.getCode());
    }

    @Test
    void previewsTransferBatchWithReservedDistinctTargetRowsAndNoWrites() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        InventoryMapper inventoryMapper = mock(InventoryMapper.class);
        PalletTaskMapper taskMapper = mock(PalletTaskMapper.class);
        ProductMapper productMapper = mock(ProductMapper.class);
        WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
        ReflectionTestUtils.setField(service, "inventoryMapper", inventoryMapper);
        ReflectionTestUtils.setField(service, "palletTaskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "productMapper", productMapper);
        ReflectionTestUtils.setField(service, "warehouseMapper", warehouseMapper);

        PalletCode first = transferPallet(10, "BT000001");
        PalletCode second = transferPallet(11, "BT000002");
        doReturn(first).when(service).parseAndFind("BT000001");
        doReturn(second).when(service).parseAndFind("BT000002");

        PalletTask firstTask = transferTask(2);
        PalletTask secondTask = transferTask(2);
        when(taskMapper.selectPendingTransferTaskByCycle(10, 1)).thenReturn(firstTask);
        when(taskMapper.selectPendingTransferTaskByCycle(11, 1)).thenReturn(secondTask);

        Inventory firstInventory = inventory(10, 1, 1);
        Inventory secondInventory = inventory(11, 1, 2);
        when(inventoryMapper.selectOne(any())).thenReturn(firstInventory, secondInventory);

        Warehouse source = warehouse(1, "1", 3);
        Warehouse target = warehouse(2, "2", 3);
        when(warehouseMapper.selectById(1)).thenReturn(source);
        when(warehouseMapper.selectById(2)).thenReturn(target);
        when(inventoryMapper.getUsedRowList(anyInt(), any(), anyInt())).thenAnswer(invocation -> {
            int warehouseId = invocation.getArgument(0);
            String side = invocation.getArgument(1);
            int layer = invocation.getArgument(2);
            if (warehouseId == 1 && "左".equals(side) && layer == 1) {
                return List.of(1, 2);
            }
            return List.of();
        });
        Product product = new Product();
        product.setCanStack(false);
        when(productMapper.selectById(84)).thenReturn(product);

        var result = service.previewTransferTasks(List.of("BT000001", "BT000002"));

        assertThat(result.getBlockingIssues()).isEmpty();
        assertThat(result.getItems()).extracting("plannedTargetRowNumber").containsExactly(1, 2);
        assertThat(result.getItems()).allSatisfy(item -> {
            assertThat(item.getTargetWarehouseName()).isEqualTo("2");
            assertThat(item.getTargetSide()).isEqualTo("左");
            assertThat(item.getPlannedTargetLayer()).isEqualTo(1);
        });
        verify(inventoryMapper, never()).updateLocation(anyInt(), anyInt(), any(), anyInt(), anyInt());
        verify(taskMapper, never()).insert(any());
        verify(warehouseMapper, never()).updateCurCapacity(anyInt(), anyInt());
    }

    private static PalletCode transferPallet(int id, String code) {
        PalletCode pallet = new PalletCode();
        pallet.setId(id);
        pallet.setCode(code);
        pallet.setStatus("INSTOCK");
        pallet.setProductStatus("成品");
        pallet.setProductId(84);
        pallet.setProductionDate(LocalDate.of(2026, 5, 22));
        pallet.setCurrentCycleNo(1);
        return pallet;
    }

    private static PalletTask transferTask(int targetWarehouseId) {
        PalletTask task = new PalletTask();
        task.setTaskType("TRANSFER");
        task.setStatus("PENDING");
        task.setTargetWarehouseId(targetWarehouseId);
        task.setTargetSide("左");
        return task;
    }

    private static Inventory inventory(int palletCodeId, int warehouseId, int rowNumber) {
        Inventory inventory = new Inventory();
        inventory.setPalletCodeId(palletCodeId);
        inventory.setWarehouseId(warehouseId);
        inventory.setSide("左");
        inventory.setRowNumber(rowNumber);
        inventory.setLayer(1);
        inventory.setQuantity(1);
        return inventory;
    }

    private static Warehouse warehouse(int id, String name, int maxRows) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setWarehouseName(name);
        warehouse.setMaxRows(maxRows);
        return warehouse;
    }
}
