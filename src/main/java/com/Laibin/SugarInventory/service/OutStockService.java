package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutStockService {
    @Transactional
    void processOutStock(OutStockRequestDTO request, Integer operatorId);

    public List<OutProductVO> searchProducts(OutProductQueryDTO query);
}
