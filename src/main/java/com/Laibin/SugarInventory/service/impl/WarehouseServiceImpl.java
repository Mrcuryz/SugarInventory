package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.WarehouseDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.WarehouseService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class WarehouseServiceImpl extends ServiceImpl<WarehouseMapper, Warehouse> implements WarehouseService, LoggableService<Warehouse> {
    @Autowired
    private WarehouseMapper warehouseMapper;

    @Override
    @Transactional
    public Warehouse setWarehouseToMaintain(Integer id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        int rows = 0;
        if (warehouse.getStatus().equals("维护"))
            rows = warehouseMapper.cancelMaintain(id);
        else
            rows = warehouseMapper.updateStatusToMaintain(id);
        if (rows < 1) {
            throw new BusinessException("修改仓库状态失败");
        }

        warehouse = warehouseMapper.selectById(id);
        return warehouse;
    }

    @Override
    public Warehouse createWarehouse(WarehouseDTO dto) {
        //检查是否有重复的warehouseName
        Warehouse warehouseByWarehouseId = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouseByWarehouseId != null) {
            throw new BusinessException("库位名称已存在");
        }

        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseName(dto.getWarehouseName());
        // 计算最大库存量，需要根据库位中已存放的产品类型来计算
        warehouse.setMaxCapacity(dto.getMaxRows() * 2);
        warehouse.setStatus("空置");
        warehouse.setMaxRows(dto.getMaxRows());
        warehouse.setCurCapacity(0);
        warehouse.setCreatedAt(LocalDateTime.now());

        // 直接调用MyBatis-Plus的insert方法
        int result = warehouseMapper.insert(warehouse);
        if (result < 1) {
            throw new BusinessException("创建仓库失败");
        }

        return warehouse;
    }

    @Override
    public Warehouse getWarehouseById(Integer id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        return warehouse;
    }

    @Override
    public Warehouse updateWarehouse(WarehouseUpdateDTO warehouse) {
        // 先查询原有仓库信息
        Warehouse oldWarehouse = warehouseMapper.selectById(warehouse.getId());
        if (oldWarehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        Warehouse newWarehouse = new Warehouse();

        // 检查是否有重复的warehouseId
        Warehouse warehouseByWarehouseId = warehouseMapper.selectByWarehouseName(warehouse.getWarehouseName());
        if (warehouseByWarehouseId != null && !warehouseByWarehouseId.getId().equals(warehouse.getId())) {
            throw new BusinessException("库位名称已存在");
        }
        newWarehouse.setWarehouseName(warehouse.getWarehouseName());

        // 如果修改了最大排数，需重新计算最大库存量，即maxCapacity = maxRows * 2。
        if (warehouse.getMaxRows() != null && !warehouse.getMaxRows().equals(oldWarehouse.getMaxRows())) {
            newWarehouse.setMaxCapacity(warehouse.getMaxRows() * 2);
        } else {
            newWarehouse.setMaxCapacity(oldWarehouse.getMaxCapacity());
        }
        newWarehouse.setMaxRows(warehouse.getMaxRows() == null ?
                oldWarehouse.getMaxRows() : warehouse.getMaxRows());
        newWarehouse.setCurCapacity(oldWarehouse.getCurCapacity());
        newWarehouse.setStatus(oldWarehouse.getStatus());
        newWarehouse.setCreatedAt(oldWarehouse.getCreatedAt());
        newWarehouse.setId(warehouse.getId());
        newWarehouse.setCreatedAt(oldWarehouse.getCreatedAt());
        // 更新时，不允许用户直接修改 status、curCapacity、createdAt 等字段
        int result = warehouseMapper.updateById(newWarehouse);
        if (result < 1) {
            throw new BusinessException("更新仓库信息失败");
        }

        // 返回修改后的仓库信息
        return warehouseMapper.selectById(warehouse.getId());
    }

    @Override
    public void deleteWarehouse(Integer id) {
        int result = warehouseMapper.deleteById(id);
        if (result < 1) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
    }

    @Override
    public List<Warehouse> listAllWarehouses(String name) {
        QueryWrapper<Warehouse> queryWrapper = new QueryWrapper<>();
        if (name != null && !name.trim().isEmpty()) {
            queryWrapper.like("warehouse_name", name);
        }
        return warehouseMapper.selectList(queryWrapper);
    }


    @Override
    public Warehouse findById(Integer id) {
        return warehouseMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "库位";
    }
}
