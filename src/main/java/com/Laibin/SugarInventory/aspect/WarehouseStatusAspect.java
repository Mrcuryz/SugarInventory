package com.Laibin.SugarInventory.aspect;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class WarehouseStatusAspect {

    private final WarehouseMapper warehouseMapper;

    @Around("@annotation(com.Laibin.SugarInventory.annotation.CheckWarehouseStatus)")
    public Object checkWarehouseStatus(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        // 这里假设第一个参数是入库/出库对象，里面有 warehouseId
        for (Object arg : args) {
            if (arg instanceof InStockRequestDTO inStockDTO) {
                validateWarehouse(inStockDTO.getWarehouseName());
            }
            if (arg instanceof OutStockRequestDTO outStockDTO) {
                validateWarehouse(outStockDTO.getWarehouseId());
            }
            if (arg instanceof AddSemiProductRecordDTO addSemiProductDTO) {
                validateWarehouse(addSemiProductDTO.getWarehouseName());
            }
        }

        return joinPoint.proceed();
    }

    private void validateWarehouse(Integer warehouseId) {
        if (warehouseId == null) return;
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse != null && "维护".equals(warehouse.getStatus())) {
            throw new BusinessException("该库位正在维护中，禁止操作！");
        }
    }

    private void validateWarehouse(String warehouseName) {
        if (warehouseName == null) return;
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(warehouseName);
        if (warehouse != null && "维护".equals(warehouse.getStatus())) {
            throw new BusinessException("该库位正在维护中，禁止操作！");
        }
    }
}
