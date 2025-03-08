package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.WarehouseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 *  服务实现类
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
    public void setWarehouseToMaintain(Integer id) {
        int rows = warehouseMapper.updateStatusToMaintain(id);
        if (rows < 1) {
            throw new BusinessException("修改仓库状态失败");
        }
    }

    @Override
    public void createWarehouse(Warehouse warehouse) {
        // 直接调用MyBatis-Plus的insert方法
        int result = warehouseMapper.insert(warehouse);
        if(result < 1){
            throw new RuntimeException("创建仓库失败");
        }
    }

    @Override
    public Warehouse getWarehouseById(Integer id) {
        return warehouseMapper.selectById(id);
    }

    @Override
    public Warehouse updateWarehouse(Warehouse warehouse) {
        // 更新时，不允许用户直接修改 status、curCapacity、createdAt 等字段
        int result = warehouseMapper.updateById(warehouse);
        if(result < 1){
            throw new RuntimeException("更新仓库信息失败");
        }

        // 返回修改后的仓库信息
        return warehouseMapper.selectById(warehouse.getId());
    }

    @Override
    public void deleteWarehouse(Integer id) {
        int result = warehouseMapper.deleteById(id);
        if(result < 1){
            throw new RuntimeException("删除仓库失败");
        }
    }

    @Override
    public List<Warehouse> listAllWarehouses() {
        return warehouseMapper.selectList(null);
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
