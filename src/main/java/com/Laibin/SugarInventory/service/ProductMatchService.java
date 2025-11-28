package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.po.Product;

import java.util.List;

public interface ProductMatchService {

    /**
     * 根据报数原文中的产品名称片段，匹配系统 Product。
     * reasons 用于记录匹配过程中的风险/说明（会影响 yellow/red 判断）。
     */
    Product matchByName(String productNameRaw, List<String> reasons);
}
