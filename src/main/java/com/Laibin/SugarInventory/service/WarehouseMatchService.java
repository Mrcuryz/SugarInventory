package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.po.Warehouse;

import java.util.List;

public interface WarehouseMatchService {

    /**
     * 根据库位提示文本（如“1号库位”“6号烘房”“柳冰”），匹配 Warehouse。
     */
    Warehouse matchByHint(String warehouseHint, List<String> reasons);
}
