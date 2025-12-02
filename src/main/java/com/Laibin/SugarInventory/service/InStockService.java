package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InStockUpdateDTO;
import com.Laibin.SugarInventory.domain.po.InStock;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
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
public interface InStockService extends IService<InStock> {
//    void handleStockIn(InStockRequestDTO request, Integer operatorId);

    @Transactional
    InVO stockIn(InStockRequestDTO dto, Integer operatorId);

    PageResult<InStockVO> queryInStockRecords(InStockQueryDTO queryDTO, User currentUser);
}
