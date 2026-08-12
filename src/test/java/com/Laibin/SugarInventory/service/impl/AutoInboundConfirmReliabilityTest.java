package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.AutoInboundTaskUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.AutoInboundExecution;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.mapper.AutoInboundExecutionMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.ProductionConsumptionRecordMapper;
import com.Laibin.SugarInventory.mapper.ProductionReportRecordMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.SemiPreparePoolBalanceMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutoInboundConfirmReliabilityTest {

    private AutoInboundParseService parseService;
    private PalletCodeService palletCodeService;
    private PalletCodeMapper palletCodeMapper;
    private ProductMapper productMapper;
    private WarehouseMapper warehouseMapper;
    private AutoInboundExecutionMapper executionMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private AutoInboundConfirmServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        parseService = mock(AutoInboundParseService.class);
        palletCodeService = mock(PalletCodeService.class);
        palletCodeMapper = mock(PalletCodeMapper.class);
        productMapper = mock(ProductMapper.class);
        warehouseMapper = mock(WarehouseMapper.class);
        executionMapper = mock(AutoInboundExecutionMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AutoInboundConfirmServiceImpl(
                parseService,
                palletCodeService,
                palletCodeMapper,
                productMapper,
                warehouseMapper,
                mock(ProductionReportRecordMapper.class),
                mock(ProductionConsumptionRecordMapper.class),
                mock(SemiPreparePoolBalanceMapper.class),
                executionMapper,
                redisTemplate,
                objectMapper);
    }

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void duplicateTaskIdsAreRejectedBeforeLockOrBusinessWrite() {
        AutoInboundConfirmRequest request = new AutoInboundConfirmRequest();
        request.setConfirmedTaskIds(List.of("task-a", "task-a"));

        assertThatThrownBy(() -> service.confirm("batch-a", request, user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(400));

        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        verifyNoBusinessWrite();
    }

    @Test
    void concurrentConfirmIsRejectedByOwnedRedisLock() {
        AutoInboundConfirmRequest request = requestFor("task-a");
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(10L), eq(TimeUnit.MINUTES)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.confirm("batch-a", request, user()))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(409);
                    assertThat(error.getMessage()).contains("正在确认");
                });

        verify(parseService, never()).getBatch(anyString(), any());
        verifyNoBusinessWrite();
    }

    @Test
    void committedDatabaseFactRecoversRetryWithoutSecondInventoryWrite() {
        User user = user();
        AutoInboundTask draft = draftTask();
        AutoInboundParseResponse batch = batch(draft);
        AutoInboundConfirmRequest request = requestFor(draft.getTaskId());
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(10L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(parseService.getBatch("batch-a", user)).thenReturn(batch);
        when(productMapper.selectById(101)).thenReturn(product());
        when(warehouseMapper.selectById(201)).thenReturn(warehouse());
        PalletCode code = new PalletCode();
        code.setId(301);
        code.setCode("AUTO-UAT-001");
        when(palletCodeMapper.selectList(any())).thenReturn(List.of(code));
        when(palletCodeService.createFixedProductInboundAndConfirm(any(), any(), eq(7), eq("AUTO_INBOUND:batch-a")))
                .thenReturn(InVO.createDefault());

        AtomicReference<AutoInboundExecution> persisted = new AtomicReference<>();
        when(executionMapper.selectOne(any())).thenAnswer(invocation -> persisted.get());
        when(executionMapper.insert(any())).thenAnswer(invocation -> {
            AutoInboundExecution execution = invocation.getArgument(0);
            execution.setId(1L);
            persisted.set(execution);
            return 1;
        });

        AutoInboundParseResponse first = invokeAndCommit("batch-a", request, user);
        assertThat(first.getTasks().get(0).getStatus()).isEqualTo("COMMITTED");
        assertThat(persisted.get().getStatus()).isEqualTo("COMMITTED");
        assertThat(persisted.get().getResultJson()).contains("AUTO-UAT-001");

        AutoInboundParseResponse replay = invokeAndCommit("batch-a", request, user);
        assertThat(replay.getTasks().get(0).getTaskItems().get(0).getCode()).isEqualTo("AUTO-UAT-001");
        verify(palletCodeService, times(1))
                .createFixedProductInboundAndConfirm(any(), any(), eq(7), eq("AUTO_INBOUND:batch-a"));
    }

    @Test
    void failedDomainWriteRestoresRedisBatchAsRetryableFailure() {
        User user = user();
        AutoInboundTask draft = draftTask();
        AutoInboundConfirmRequest request = requestFor(draft.getTaskId());
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(10L), eq(TimeUnit.MINUTES)))
                .thenReturn(true);
        when(parseService.getBatch("batch-a", user)).thenReturn(batch(draft));
        when(productMapper.selectById(101)).thenReturn(product());
        when(warehouseMapper.selectById(201)).thenReturn(warehouse());
        PalletCode code = new PalletCode();
        code.setId(301);
        code.setCode("AUTO-UAT-001");
        when(palletCodeMapper.selectList(any())).thenReturn(List.of(code));
        when(executionMapper.selectOne(any())).thenReturn(null);
        when(executionMapper.insert(any())).thenAnswer(invocation -> {
            ((AutoInboundExecution) invocation.getArgument(0)).setId(1L);
            return 1;
        });
        when(palletCodeService.createFixedProductInboundAndConfirm(any(), any(), eq(7), anyString()))
                .thenThrow(new BusinessException(500, "模拟领域事务失败"));

        TransactionSynchronizationManager.initSynchronization();
        assertThatThrownBy(() -> service.confirm("batch-a", request, user))
                .isInstanceOf(BusinessException.class);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AutoInboundTask>> tasksCaptor = ArgumentCaptor.forClass(List.class);
        verify(parseService, times(2)).saveBatch(eq("batch-a"), tasksCaptor.capture(), eq(user));
        List<AutoInboundTask> restored = tasksCaptor.getAllValues().get(1);
        assertThat(restored.get(0).getStatus()).isEqualTo("FAILED");
        assertThat(restored.get(0).getWarnings()).anyMatch(warning -> warning.contains("模拟领域事务失败"));
    }

    private AutoInboundParseResponse invokeAndCommit(String batchId, AutoInboundConfirmRequest request, User user) {
        TransactionSynchronizationManager.initSynchronization();
        AutoInboundParseResponse response = service.confirm(batchId, request, user);
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        return response;
    }

    private AutoInboundConfirmRequest requestFor(String taskId) {
        AutoInboundTaskUpdateDTO update = new AutoInboundTaskUpdateDTO();
        update.setTaskId(taskId);
        update.setEntryDate(LocalDate.of(2026, 8, 12));
        update.setSide("左");
        update.setSemiProductId(101);
        update.setSemiWarehouseName("UAT库位");
        update.setSemiBoardQuantity(1);
        update.setSemiPieceQuantity(0);
        AutoInboundConfirmRequest request = new AutoInboundConfirmRequest();
        request.setConfirmedTaskIds(List.of(taskId));
        request.setUpdatedTasks(List.of(update));
        return request;
    }

    private AutoInboundTask draftTask() {
        AutoInboundTask task = new AutoInboundTask();
        task.setTaskId("task-a");
        task.setBatchId("batch-a");
        task.setType(AutoInboundType.SEMI_PRODUCT);
        task.setStatus("DRAFT");
        task.setEntryDate(LocalDate.of(2026, 8, 12));
        task.setSide("左");
        task.setSemiProductId(101);
        task.setSemiProductName("UAT半成品");
        task.setSemiWarehouseId(201);
        task.setSemiWarehouseName("UAT库位");
        task.setSemiBoardQuantity(1);
        task.setSemiPieceQuantity(0);
        task.setWarnings(List.of());
        task.setMissingFields(List.of());
        task.setCanAutoStockIn(true);
        return task;
    }

    private AutoInboundParseResponse batch(AutoInboundTask task) {
        AutoInboundParseResponse response = new AutoInboundParseResponse();
        response.setBatchId("batch-a");
        response.setTasks(List.of(task));
        return response;
    }

    private Product product() {
        Product product = new Product();
        product.setId(101);
        product.setProductName("UAT半成品");
        product.setStatus("半成品");
        product.setPiecesPerPallet(25);
        return product;
    }

    private Warehouse warehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(201);
        warehouse.setWarehouseName("UAT库位");
        return warehouse;
    }

    private User user() {
        User user = new User();
        user.setId(7);
        return user;
    }

    private void verifyNoBusinessWrite() {
        verify(palletCodeService, never()).createFixedProductInboundAndConfirm(any(), any(), any(), any());
        verify(executionMapper, never()).insert(any());
    }
}
