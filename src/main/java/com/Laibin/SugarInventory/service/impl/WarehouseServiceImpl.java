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
        int maxCapacity = resolveMaxCapacity(dto.getMaxCapacity(), dto.getMaxRows());
        warehouse.setMaxCapacity(maxCapacity);
        warehouse.setStatus("空置");
        // 数据库字段保留用于兼容；不再表示真实排数。
        warehouse.setMaxRows(maxCapacity);
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

        int maxCapacity = warehouse.getMaxCapacity() == null && warehouse.getMaxRows() == null
                ? oldWarehouse.getMaxCapacity()
                : resolveMaxCapacity(warehouse.getMaxCapacity(), warehouse.getMaxRows());
        if (oldWarehouse.getCurCapacity() != null && maxCapacity < oldWarehouse.getCurCapacity()) {
            throw new BusinessException("最大容量不能小于当前库存量");
        }
        newWarehouse.setMaxCapacity(maxCapacity);
        newWarehouse.setMaxRows(maxCapacity);
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

    private int resolveMaxCapacity(Integer maxCapacity, Integer legacyMaxRows) {
        Integer resolved = maxCapacity;
        if (resolved == null && legacyMaxRows != null) {
            resolved = legacyMaxRows * 2;
        }
        if (resolved == null || resolved <= 0) {
            throw new BusinessException("最大容量必须大于0");
        }
        return resolved;
    }
}
