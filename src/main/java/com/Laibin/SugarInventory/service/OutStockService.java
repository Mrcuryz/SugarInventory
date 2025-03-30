package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutStockRecordVO;
import com.Laibin.SugarInventory.domain.vo.OutVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutStockService {
    @Transactional
    OutVO processOutStock(OutStockRequestDTO request, Integer operatorId);

    PageResult<OutStockRecordVO> searchOutRecords(OutRecordQueryDTO query);
}
