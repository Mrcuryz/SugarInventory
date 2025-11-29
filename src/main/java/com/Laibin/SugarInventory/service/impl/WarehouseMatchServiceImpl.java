package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.WarehouseMatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseMatchServiceImpl implements WarehouseMatchService {

    private final WarehouseMapper warehouseMapper;

    @Override
    public Warehouse matchByHint(String warehouseHint, List<String> reasons) {
        if (warehouseHint == null || warehouseHint.isBlank()) {
            reasons.add("缺少库位信息（未识别出仓库提示）");
            return null;
        }

        // 1. 精确匹配
        Warehouse exact = warehouseMapper.selectOne(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseName, warehouseHint)
                .last("limit 1"));
        if (exact != null) {
            return exact;
        }

        // 2. 模糊匹配
        List<Warehouse> fuzzy = warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>()
                .like(Warehouse::getWarehouseName, warehouseHint));
        if (fuzzy.size() == 1) {
            reasons.add("通过模糊匹配找到库位：" + fuzzy.get(0).getWarehouseName());
            return fuzzy.get(0);
        }
        if (fuzzy.isEmpty()) {
            reasons.add("未匹配到库位：" + warehouseHint);
            return null;
        }

        reasons.add("匹配到多个库位，请人工确认：" +
                fuzzy.stream().map(Warehouse::getWarehouseName).collect(Collectors.joining(" / ")));
        return fuzzy.get(0);
    }
}

