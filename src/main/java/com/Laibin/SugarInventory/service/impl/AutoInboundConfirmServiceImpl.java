package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.enumObject.AutoInboundType;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.redis.AutoInboundTask;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AutoInboundConfirmServiceImpl implements AutoInboundConfirmService {

    private final AutoInboundParseService autoInboundParseService;
    private final SemiProductRecordService semiProductRecordService;
    private final InStockService inStockService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_PREFIX = "auto_inbound:batch:";

    @Transactional
    @Override
    public void confirm(String batchId, AutoInboundConfirmRequest req, User user) {
        req.setOperator(user);
        AutoInboundParseResponse batch = autoInboundParseService.getBatch(batchId);
        List<AutoInboundTask> tasks = batch.getTasks();

        Map<String, AutoInboundTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(AutoInboundTask::getTaskId, t -> t));

        Map<String, AutoInboundTask> updatedMap = Optional.ofNullable(req.getUpdatedTasks())
                .orElse(List.of())
                .stream()
                .collect(Collectors.toMap(AutoInboundTask::getTaskId, t -> t));

        for (String taskId : req.getConfirmedTaskIds()) {
            AutoInboundTask task = taskMap.get(taskId);
            if (task == null) continue;
            if (updatedMap.containsKey(taskId)) {
                task = updatedMap.get(taskId);
            }

            if (task.getType() == AutoInboundType.SEMI_PRODUCT) {
                doSemiStockIn(task, req.getOperator());
            } else if (task.getType() == AutoInboundType.FINISHED_PRODUCT) {
                doFinishedStockIn(task, req.getOperator());
            }
        }

        // 清空redis缓存
        stringRedisTemplate.delete(REDIS_PREFIX + batchId);
    }

    private void doSemiStockIn(AutoInboundTask task, User operator) {
        AddSemiProductRecordDTO dto = new AddSemiProductRecordDTO();
        dto.setProductId(task.getSemiProductId());
        dto.setWarehouseName(task.getSemiWarehouseName());
        dto.setEntryDate(task.getEntryDate());

        if (task.getSemiBoardQuantity() != null && task.getSemiBoardQuantity() > 0) {
            dto.setQuantity(task.getSemiBoardQuantity());
            dto.setUnit("0");
            semiProductRecordService.addSemiProductRecord(dto, operator.getName());
        }
        if (task.getSemiPieceQuantity() != null && task.getSemiPieceQuantity() > 0) {
            dto.setQuantity(task.getSemiPieceQuantity());
            dto.setUnit("1");
            semiProductRecordService.addSemiProductRecord(dto, operator.getName());
        }
    }

    private void doFinishedStockIn(AutoInboundTask task, User operator) {
        if (task.getFinishedBoardQuantity() != null && task.getFinishedBoardQuantity() > 0) {
            InStockRequestDTO dto = buildFinishedDto(task, "0", task.getFinishedBoardQuantity());
            inStockService.stockIn(dto, operator.getId());
        }
        if (task.getFinishedPieceQuantity() != null && task.getFinishedPieceQuantity() > 0) {
            InStockRequestDTO dto = buildFinishedDto(task, "1", task.getFinishedPieceQuantity());
            inStockService.stockIn(dto, operator.getId());
        }
    }

    private InStockRequestDTO buildFinishedDto(AutoInboundTask task, String unit, Integer quantity) {
        InStockRequestDTO dto = new InStockRequestDTO();
        dto.setProductId(task.getProductId());
        dto.setWarehouseName(task.getWarehouseName());
        dto.setEntryDate(task.getEntryDate());
        dto.setQuantity(quantity);
        dto.setUnit(unit);
        dto.setSide(task.getSide());
        dto.setReturnInStockFlag("0");
        dto.setSemiRecords(
                task.getSuggestedSemiRecords() != null ? task.getSuggestedSemiRecords()
                        : Collections.emptyList()
        );
        return dto;
    }
}
