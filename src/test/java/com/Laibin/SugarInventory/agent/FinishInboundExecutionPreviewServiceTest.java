package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionPreviewService;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewItemDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.domain.po.PalletTaskSemiItem;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.SemiPreparePoolBalance;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPreviewVO;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskSemiItemMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.SemiPreparePoolBalanceMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputCodeMapper;
import com.Laibin.SugarInventory.service.PalletCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FinishInboundExecutionPreviewServiceTest {
    private PalletCodeService palletCodeService;
    private PalletTaskMapper palletTaskMapper;
    private ProductMapper productMapper;
    private WarehouseMapper warehouseMapper;
    private InventoryMapper inventoryMapper;
    private ProductionOrderOutputCodeMapper outputCodeMapper;
    private PalletTaskSemiItemMapper semiItemMapper;
    private SemiPreparePoolBalanceMapper balanceMapper;
    private FinishInboundExecutionPreviewService service;

    @BeforeEach
    void setUp() {
        palletCodeService = mock(PalletCodeService.class);
        palletTaskMapper = mock(PalletTaskMapper.class);
        productMapper = mock(ProductMapper.class);
        warehouseMapper = mock(WarehouseMapper.class);
        inventoryMapper = mock(InventoryMapper.class);
        outputCodeMapper = mock(ProductionOrderOutputCodeMapper.class);
        semiItemMapper = mock(PalletTaskSemiItemMapper.class);
        balanceMapper = mock(SemiPreparePoolBalanceMapper.class);
        service = new FinishInboundExecutionPreviewService(
                palletCodeService,
                palletTaskMapper,
                productMapper,
                warehouseMapper,
                inventoryMapper,
                outputCodeMapper,
                semiItemMapper,
                balanceMapper);
    }

    @Test
    void createsReadySnapshotAndUsesServerAuthoritativeProductionOutput() {
        PalletCode pallet = pendingPallet();
        PalletTask task = finishInboundTask();
        Product product = product();
        Warehouse warehouse = warehouse();
        ProductionOrderOutputCode output = new ProductionOrderOutputCode();
        output.setId(91L);
        output.setStatus("PRINTED");
        output.setQuantity(30);
        output.setUnit("1");
        output.setInventoryId(null);
        when(palletCodeService.parseAndFind("BT0019N1")).thenReturn(pallet);
        when(palletTaskMapper.selectPendingByCycle(11, 2)).thenReturn(task);
        when(productMapper.selectById(31)).thenReturn(product);
        when(warehouseMapper.selectByWarehouseName("1号库位")).thenReturn(warehouse);
        when(outputCodeMapper.selectByTaskId(21)).thenReturn(output);
        when(semiItemMapper.selectList(any())).thenReturn(List.of());

        FinishInboundExecutionPreviewVO result = service.preview(request(99, "0"), user());

        assertThat(result.getPreviewStatus()).isEqualTo("READY");
        assertThat(result.isReadyForUserConfirmation()).isTrue();
        assertThat(result.getBlockingIssues()).isEmpty();
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getPalletCode()).isEqualTo("BT0019N1");
            assertThat(item.getWarehouseName()).isEqualTo("1号库位");
            assertThat(item.getQuantity()).isEqualTo(30);
            assertThat(item.getUnitLabel()).isEqualTo("件");
            assertThat(item.isQuantityLockedByProductionOutput()).isTrue();
        });
        assertThat(result.getServerSnapshot()).isNotNull();
        assertThat(result.getServerSnapshot().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTask().getId()).isEqualTo(21);
            assertThat(item.getPallet().getId()).isEqualTo(11);
            assertThat(item.getProduct().getId()).isEqualTo(31);
            assertThat(item.getWarehouse().getId()).isEqualTo(41);
            assertThat(item.getWarehouse().getMaxRows()).isEqualTo(20);
            assertThat(item.getWarehouse().getLeftUsedRowsLayer1()).isEmpty();
            assertThat(item.getNormalizedInput().getQuantity()).isEqualTo(30);
            assertThat(item.getNormalizedInput().getUnit()).isEqualTo("1");
        });
    }

    @Test
    void mirrorsExistingConsumptionRuleForNonPositiveLegacyMaterialQuantity() {
        PalletCode pallet = pendingPallet();
        PalletTask task = finishInboundTask();
        when(palletCodeService.parseAndFind("BT0019N1")).thenReturn(pallet);
        when(palletTaskMapper.selectPendingByCycle(11, 2)).thenReturn(task);
        when(productMapper.selectById(31)).thenReturn(product());
        when(warehouseMapper.selectByWarehouseName("1号库位")).thenReturn(warehouse());
        when(outputCodeMapper.selectByTaskId(21)).thenReturn(null);

        PalletTaskSemiItem material = new PalletTaskSemiItem();
        material.setId(61);
        material.setPrepareBalanceId(71L);
        material.setQuantity(0);
        material.setTotalPieces(null);
        when(semiItemMapper.selectList(any())).thenReturn(List.of(material));
        SemiPreparePoolBalance balance = new SemiPreparePoolBalance();
        balance.setId(71L);
        balance.setStatus("ACTIVE");
        balance.setRemainingPieces(0);
        when(balanceMapper.selectById(71L)).thenReturn(balance);

        FinishInboundExecutionPreviewVO result = service.preview(request(1, "0"), user());

        assertThat(result.getPreviewStatus()).isEqualTo("READY");
        assertThat(result.getServerSnapshot().getItems().get(0).getMaterialBalances())
                .singleElement()
                .satisfies(state -> assertThat(state.getRemainingPieces()).isZero());
    }

    @Test
    void returnsConflictWithoutServerSnapshotWhenCurrentTaskIsNotFinishInbound() {
        PalletCode pallet = pendingPallet();
        PalletTask task = finishInboundTask();
        task.setTaskType("FINISH_OUT");
        when(palletCodeService.parseAndFind("BT0019N1")).thenReturn(pallet);
        when(palletTaskMapper.selectPendingByCycle(11, 2)).thenReturn(task);

        FinishInboundExecutionPreviewVO result = service.preview(request(1, "0"), user());

        assertThat(result.getPreviewStatus()).isEqualTo("CONFLICT");
        assertThat(result.isReadyForUserConfirmation()).isFalse();
        assertThat(result.getEligibleItemCount()).isZero();
        assertThat(result.getServerSnapshot()).isNull();
        assertThat(result.getBlockingIssues()).singleElement()
                .asString()
                .contains("待处理成品入库任务");
    }

    @Test
    void rejectsPreviewWhenExistingAllocationRulesHaveNoAvailablePosition() {
        PalletCode pallet = pendingPallet();
        PalletTask task = finishInboundTask();
        Product product = product();
        product.setCanStack(false);
        Warehouse warehouse = warehouse();
        warehouse.setMaxRows(1);
        when(palletCodeService.parseAndFind("BT0019N1")).thenReturn(pallet);
        when(palletTaskMapper.selectPendingByCycle(11, 2)).thenReturn(task);
        when(productMapper.selectById(31)).thenReturn(product);
        when(warehouseMapper.selectByWarehouseName("1号库位")).thenReturn(warehouse);
        when(outputCodeMapper.selectByTaskId(21)).thenReturn(null);
        when(inventoryMapper.getUsedRowList(41, "左", 1)).thenReturn(List.of(1));
        when(inventoryMapper.getUsedRowList(41, "右", 1)).thenReturn(List.of(1));
        when(inventoryMapper.getUsedRowList(41, "左", 2)).thenReturn(List.of());
        when(inventoryMapper.getUsedRowList(41, "右", 2)).thenReturn(List.of());

        FinishInboundExecutionPreviewVO result = service.preview(request(1, "0"), user());

        assertThat(result.getPreviewStatus()).isEqualTo("CONFLICT");
        assertThat(result.isReadyForUserConfirmation()).isFalse();
        assertThat(result.getServerSnapshot()).isNull();
        assertThat(result.getBlockingIssues()).singleElement().asString().contains("没有可分配位置");
    }

    @Test
    void reservesPositionsAcrossTheWholePreviewBatch() {
        PalletCode firstPallet = pendingPallet();
        PalletCode secondPallet = pendingPallet();
        secondPallet.setId(12);
        secondPallet.setCode("BT0019N2");
        PalletTask firstTask = finishInboundTask();
        PalletTask secondTask = finishInboundTask();
        secondTask.setId(22);
        secondTask.setPalletCodeId(12);
        Product product = product();
        product.setCanStack(false);
        Warehouse warehouse = warehouse();
        warehouse.setMaxRows(1);
        when(palletCodeService.parseAndFind("BT0019N1")).thenReturn(firstPallet);
        when(palletCodeService.parseAndFind("BT0019N2")).thenReturn(secondPallet);
        when(palletTaskMapper.selectPendingByCycle(11, 2)).thenReturn(firstTask);
        when(palletTaskMapper.selectPendingByCycle(12, 2)).thenReturn(secondTask);
        when(productMapper.selectById(31)).thenReturn(product);
        when(warehouseMapper.selectByWarehouseName("1号库位")).thenReturn(warehouse);
        when(outputCodeMapper.selectByTaskId(any())).thenReturn(null);
        when(semiItemMapper.selectList(any())).thenReturn(List.of());
        when(inventoryMapper.getUsedRowList(41, "左", 1)).thenReturn(List.of());
        when(inventoryMapper.getUsedRowList(41, "右", 1)).thenReturn(List.of(1));
        when(inventoryMapper.getUsedRowList(41, "左", 2)).thenReturn(List.of());
        when(inventoryMapper.getUsedRowList(41, "右", 2)).thenReturn(List.of());

        FinishInboundExecutionPreviewDTO request = new FinishInboundExecutionPreviewDTO();
        request.setPreviewVersion(1);
        FinishInboundExecutionPreviewItemDTO first = request(1, "0").getItems().get(0);
        FinishInboundExecutionPreviewItemDTO second = request(1, "0").getItems().get(0);
        second.setCode("BT0019N2");
        request.setItems(List.of(first, second));

        FinishInboundExecutionPreviewVO result = service.preview(request, user());

        assertThat(result.getPreviewStatus()).isEqualTo("CONFLICT");
        assertThat(result.getEligibleItemCount()).isEqualTo(1);
        assertThat(result.getServerSnapshot()).isNull();
        assertThat(result.getBlockingIssues()).singleElement().asString().contains("BT0019N2", "没有可分配位置");
    }

    private static FinishInboundExecutionPreviewDTO request(int quantity, String unit) {
        FinishInboundExecutionPreviewItemDTO item = new FinishInboundExecutionPreviewItemDTO();
        item.setCode("bt0019n1");
        item.setWarehouseName("1号库位");
        item.setEntryDate(LocalDate.of(2026, 8, 9));
        item.setSide("左");
        item.setQuantity(quantity);
        item.setUnit(unit);
        item.setRemark("验收预览");
        FinishInboundExecutionPreviewDTO request = new FinishInboundExecutionPreviewDTO();
        request.setPreviewVersion(1);
        request.setItems(List.of(item));
        return request;
    }

    private static PalletCode pendingPallet() {
        PalletCode pallet = new PalletCode();
        pallet.setId(11);
        pallet.setCode("BT0019N1");
        pallet.setStatus("PENDING");
        pallet.setProductId(31);
        pallet.setCurrentCycleNo(2);
        return pallet;
    }

    private static PalletTask finishInboundTask() {
        PalletTask task = new PalletTask();
        task.setId(21);
        task.setPalletCodeId(11);
        task.setTaskType("FINISH_IN");
        task.setStatus("PENDING");
        task.setProductId(31);
        task.setProductionDate(LocalDate.of(2026, 8, 8));
        task.setCycleNo(2);
        return task;
    }

    private static Product product() {
        Product product = new Product();
        product.setId(31);
        product.setProductName("黄冰糖（袋）");
        product.setStatus("ENABLED");
        product.setPiecesPerPallet(40);
        product.setCanStack(true);
        return product;
    }

    private static Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(41);
        warehouse.setWarehouseName("1号库位");
        warehouse.setStatus("EMPTY");
        warehouse.setMaxRows(20);
        warehouse.setCurCapacity(0);
        return warehouse;
    }

    private static User user() {
        User user = new User();
        user.setId(7);
        return user;
    }
}
