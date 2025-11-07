package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutStockRequestDTO;
import com.Laibin.SugarInventory.domain.dto.TransferOutStockRequestDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface OutStockService {
    @Transactional
    OutVO processOutStock(OutStockRequestDTO request, Integer operatorId);

    /**
     * 出库扣减库存
     *
     * @param product     出库产品
     * @param warehouseId 出库仓库
     * @param entryDate   生成日期
     * @param quantity    出库数量
     * @param unit        出库数量单位：0板 1 件
     * @param operatorId  操作人ID
     * @param outType     出库类型：0整板优先 1 散件优先
     */
    void outStock(Product product, Integer warehouseId, LocalDate entryDate,
                  Integer quantity, String unit, Integer operatorId,
                  Integer outType);

    @Transactional
    OutVO processStackOutStock(OutStockRequestDTO dto, Integer operatorId);

    PageResult<OutStockRecordVO> searchOutRecords(OutRecordQueryDTO query, User user);

    /**
     * 调拨出库
     */
    InVO transferOut(TransferOutStockRequestDTO request, Integer id);
}
