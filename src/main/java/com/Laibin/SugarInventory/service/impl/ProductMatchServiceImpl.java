package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.ProductMatchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductMatchServiceImpl implements ProductMatchService {

    private final ProductMapper productMapper;

    @Override
    public Product matchByName(String productNameRaw, List<String> reasons) {
        if (productNameRaw == null || productNameRaw.isBlank()) {
            reasons.add("未解析出产品名称");
            return null;
        }

        // 1. 精确匹配
        Product exact = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getProductName, productNameRaw)
                .last("limit 1"));
        if (exact != null) {
            return exact;
        }

        // 2. 模糊匹配
        List<Product> fuzzy = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .like(Product::getProductName, productNameRaw));

        if (fuzzy.size() == 1) {
            reasons.add("通过模糊匹配找到产品：" + fuzzy.get(0).getProductName());
            return fuzzy.get(0);
        }
        if (fuzzy.isEmpty()) {
            reasons.add("未匹配到产品：" + productNameRaw);
            return null;
        }

        reasons.add("匹配到多个产品，请人工确认：" +
                fuzzy.stream().map(Product::getProductName).collect(Collectors.joining(" / ")));
        return fuzzy.get(0);
    }
}

