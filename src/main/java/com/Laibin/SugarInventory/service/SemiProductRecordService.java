package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.BaseInStockDTO;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.Laibin.SugarInventory.domain.dto.SemiProductRecordDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface SemiProductRecordService extends IService<SemiProductRecord> {
    InVO addSemiProductRecord(AddSemiProductRecordDTO dto, String operator);

    List<RecordDetailVO> getRecordsByOperator(String openid, LocalDate date);

    PageResult<RecordDetailVO> getSemiProductRecords(SemiProductRecordDTO dto, User currentUser);

    List<RecordDetailVO> getSemiProductRecordsByIds(List<Integer> ids);

    RecordDetailVO getSemiProductRecord(Integer id);

    @Transactional
    InVO stackModeInStock(AddSemiProductRecordDTO dto, String operator);

    /**
     * 处理整板入库
     *
     * @param dto       入库请求DTO
     * @param product   产品
     * @param warehouse 仓库
     * @param assay     考核
     * @param isPieces  是否件
     * @param piecesNum 件数
     * @return 入库结果
     */
    InVO handlerInStock(BaseInStockDTO dto, Product product, Warehouse warehouse,
                        Assay assay, Boolean isPieces, Integer piecesNum);

    /**
     * 处理件入库
     *
     * @param dto       入库请求DTO
     * @param product   产品
     * @param warehouse 仓库
     * @param assay     考核
     * @return 入库结果
     */
    InVO handlerInStockPieces(BaseInStockDTO dto, Product product, Warehouse warehouse, Assay assay);
}
