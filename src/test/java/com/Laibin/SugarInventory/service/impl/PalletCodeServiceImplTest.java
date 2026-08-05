package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
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
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

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

    @Test
    void rejectsInboundBatchOverTwentyBeforeResolvingAnyPallet() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        ConfirmPalletInBatchDTO dto = new ConfirmPalletInBatchDTO();
        dto.setItems(IntStream.rangeClosed(1, 21)
                .mapToObj(index -> inboundItem("BT" + index))
                .toList());

        assertThatThrownBy(() -> service.confirmFinishedTaskInBatch(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单次最多处理20个托盘");

        verify(service, never()).parseAndFind(any());
    }

    @Test
    void rejectsDifferentInputsResolvingToSamePalletBeforeBusinessWrites() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        PalletCode pallet = pallet(10, "BT000001", "PENDING");
        doReturn(pallet).when(service).parseAndFind("BT000001");
        doReturn(pallet).when(service).parseAndFind("LB|ORDER-LABEL");

        ConfirmPalletInBatchDTO dto = inboundBatch(
                inboundItem("BT000001"),
                inboundItem("LB|order-label"));

        assertThatThrownBy(() -> service.confirmFinishedTaskInBatch(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许重复扫码同一托盘");

    }

    @Test
    void confirmsInboundBatchInStablePalletIdOrder() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        doReturn(pallet(20, "BT000002", "PENDING")).when(service).parseAndFind("BT000002");
        doReturn(pallet(10, "BT000001", "PENDING")).when(service).parseAndFind("BT000001");
        ConfirmPalletInBatchDTO dto = inboundBatch(
                inboundItem("bt000002"),
                inboundItem(" BT000001 "));

        var resolved = service.resolveAndSortInboundBatchItems(dto.getItems());

        assertThat(resolved).extracting(PalletCodeServiceImpl.ResolvedInboundBatchItem::palletCodeId)
                .containsExactly(10, 20);
        assertThat(resolved).extracting(item -> item.request().getCode())
                .containsExactly("BT000001", "BT000002");
    }

    @Test
    void rejectsChangedLabelAssociationBeforeAcquiringAnUnexpectedLock() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        PalletCodeMapper palletMapper = mock(PalletCodeMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", palletMapper);
        doReturn(pallet(10, "BT000001", "PENDING"), pallet(11, "BT000011", "PENDING"))
                .when(service).parseAndFind("LB|ORDER-LABEL");

        ConfirmPalletInBatchDTO dto = inboundBatch(inboundItem("LB|order-label"));

        assertThatThrownBy(() -> service.confirmFinishedTaskInBatch(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("托盘码关联已变化");
        verify(palletMapper, never()).selectByIdForUpdate(anyInt());
    }

    @Test
    void keepsWholeInboundBatchOnOneTransactionalBoundary() throws Exception {
        Method batchMethod = PalletCodeServiceImpl.class.getMethod(
                "confirmFinishedTaskInBatch", ConfirmPalletInBatchDTO.class, Integer.class);
        assertThat(batchMethod.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void confirmAndCancelBothAcquireTheSamePalletRowLock() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        PalletCodeMapper palletMapper = mock(PalletCodeMapper.class);
        PalletTaskMapper taskMapper = mock(PalletTaskMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", palletMapper);
        ReflectionTestUtils.setField(service, "palletTaskMapper", taskMapper);

        PalletCode unresolved = pallet(10, "BT000001", "PENDING");
        PalletCode lockedForConfirm = pallet(10, "BT000001", "FREE");
        PalletCode lockedForCancel = pallet(10, "BT000001", "PENDING");
        doReturn(unresolved).when(service).parseAndFind("BT000001");
        when(palletMapper.selectByIdForUpdate(10)).thenReturn(lockedForConfirm, lockedForCancel);

        ConfirmPalletInItemDTO confirm = inboundItem("BT000001");
        assertThatThrownBy(() -> service.confirmSingleFinishedTaskIn(confirm, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不支持入库确认");

        when(taskMapper.selectPendingInboundByCycleForUpdate(10, 1)).thenReturn(List.of());
        CancelPalletBatchDTO cancel = new CancelPalletBatchDTO();
        cancel.setCodes(List.of("bt000001"));
        service.cancelTasksByCodes(cancel, 7);

        verify(palletMapper, times(2)).selectByIdForUpdate(10);
        verify(taskMapper).selectPendingInboundByCycleForUpdate(10, 1);
    }

    @Test
    void palletLockMapperUsesDatabaseForUpdateClause() throws Exception {
        Method lockMethod = PalletCodeMapper.class.getMethod("selectByIdForUpdate", Integer.class);
        Select select = lockMethod.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value())).containsIgnoringCase("FOR UPDATE");
    }

    @Test
    void pendingInboundTaskLockMapperUsesCurrentReadForUpdateClause() throws Exception {
        Method lockMethod = PalletTaskMapper.class.getMethod(
                "selectPendingInboundByCycleForUpdate", Integer.class, Integer.class);
        Select select = lockMethod.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value())).containsIgnoringCase("FOR UPDATE");
    }

    @Test
    void doesNotCancelStalePendingTaskAfterLockedPalletWasConfirmed() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        PalletCodeMapper palletMapper = mock(PalletCodeMapper.class);
        PalletTaskMapper taskMapper = mock(PalletTaskMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", palletMapper);
        ReflectionTestUtils.setField(service, "palletTaskMapper", taskMapper);

        doReturn(pallet(10, "BT000001", "PENDING")).when(service).parseAndFind("BT000001");
        when(palletMapper.selectByIdForUpdate(10)).thenReturn(pallet(10, "BT000001", "INSTOCK"));
        CancelPalletBatchDTO dto = new CancelPalletBatchDTO();
        dto.setCodes(List.of("BT000001"));

        service.cancelTasksByCodes(dto, 7);

        verify(taskMapper, never()).selectPendingInboundByCycleForUpdate(anyInt(), anyInt());
        verify(taskMapper, never()).updateById(any());
    }

    @Test
    void cancelsBatchInStablePalletIdLockOrder() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        PalletCodeMapper palletMapper = mock(PalletCodeMapper.class);
        PalletTaskMapper taskMapper = mock(PalletTaskMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", palletMapper);
        ReflectionTestUtils.setField(service, "palletTaskMapper", taskMapper);

        PalletCode first = pallet(10, "BT000001", "PENDING");
        PalletCode second = pallet(20, "BT000002", "PENDING");
        doReturn(first).when(service).parseAndFind("BT000001");
        doReturn(second).when(service).parseAndFind("BT000002");
        when(palletMapper.selectByIdForUpdate(10)).thenReturn(first);
        when(palletMapper.selectByIdForUpdate(20)).thenReturn(second);
        when(taskMapper.selectPendingInboundByCycleForUpdate(anyInt(), anyInt())).thenReturn(List.of());

        CancelPalletBatchDTO dto = new CancelPalletBatchDTO();
        dto.setCodes(List.of("BT000002", "BT000001"));
        service.cancelTasksByCodes(dto, 7);

        var lockOrder = inOrder(palletMapper);
        lockOrder.verify(palletMapper).selectByIdForUpdate(10);
        lockOrder.verify(palletMapper).selectByIdForUpdate(20);
    }

    @Test
    void rejectsCancelBatchOverTwentyBeforeResolvingAnyPallet() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        CancelPalletBatchDTO dto = new CancelPalletBatchDTO();
        dto.setCodes(IntStream.rangeClosed(1, 21)
                .mapToObj(index -> "BT" + index)
                .toList());

        assertThatThrownBy(() -> service.cancelTasksByCodes(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单次最多处理20个托盘");
        verify(service, never()).parseAndFind(any());
    }

    @Test
    void rejectsCancelAliasesResolvingToSamePalletBeforeAcquiringLocks() {
        PalletCodeServiceImpl service = spy(new PalletCodeServiceImpl());
        PalletCodeMapper palletMapper = mock(PalletCodeMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", palletMapper);
        PalletCode pallet = pallet(10, "BT000001", "PENDING");
        doReturn(pallet).when(service).parseAndFind("BT000001");
        doReturn(pallet).when(service).parseAndFind("LB|ORDER-LABEL");
        CancelPalletBatchDTO dto = new CancelPalletBatchDTO();
        dto.setCodes(List.of("BT000001", "LB|order-label"));

        assertThatThrownBy(() -> service.cancelTasksByCodes(dto, 7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许重复扫码同一托盘");
        verify(palletMapper, never()).selectByIdForUpdate(anyInt());
    }

    private static ConfirmPalletInBatchDTO inboundBatch(ConfirmPalletInItemDTO... items) {
        ConfirmPalletInBatchDTO dto = new ConfirmPalletInBatchDTO();
        dto.setItems(List.of(items));
        return dto;
    }

    private static ConfirmPalletInItemDTO inboundItem(String code) {
        ConfirmPalletInItemDTO item = new ConfirmPalletInItemDTO();
        item.setCode(code);
        item.setWarehouseName("1");
        return item;
    }

    private static PalletCode pallet(int id, String code, String status) {
        PalletCode pallet = new PalletCode();
        pallet.setId(id);
        pallet.setCode(code);
        pallet.setStatus(status);
        pallet.setCurrentCycleNo(1);
        return pallet;
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
