package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.WarehouseDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseUpdateDTO;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface WarehouseService extends IService<Warehouse> {

    @Transactional
    Warehouse setWarehouseToMaintain(Integer id);

    // 新增库位
    Warehouse createWarehouse(WarehouseDTO warehouse);

    // 根据ID查询库位
    Warehouse getWarehouseById(Integer id);

    // 修改库位信息（不允许直接修改 status、curCapacity、createdAt，由触发器和系统自动管理）
    Warehouse updateWarehouse(WarehouseUpdateDTO warehouse);

    // 删除库位
    void deleteWarehouse(Integer id);

    List<Warehouse> listAllWarehouses(String name);
}
