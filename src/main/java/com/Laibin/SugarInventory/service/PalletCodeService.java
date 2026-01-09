package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.BindTaskSemiItemsDTO;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.PalletAssayVO;
import com.Laibin.SugarInventory.domain.vo.PalletBindResultVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodeInfoVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.TaskSemiItemVO;
import com.Laibin.SugarInventory.common.PageResult;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 托盘码服务
 * </p>
 *
 * @author Mrcury
 * @since 2025-12-08
 */
public interface PalletCodeService extends IService<PalletCode> {

    List<PalletCode> generateCodes(int count, Integer userId);

    PalletCode parseAndFind(String rawCode);

    PalletCodeInfoVO parseAndGetInfo(String rawCode);

    PageResult<PalletCodePageVO> pagePalletCodes(PalletCodeQueryDTO queryDTO);

    PalletAssayVO getAssayByCode(String code);

    PalletInventoryVO getInventoryByCode(String code);

    PalletBindResultVO bindPalletAndCreateTask(BindPalletTaskDTO dto, Integer operatorId);

    List<TaskSemiItemVO> bindSemiItemsToTask(BindTaskSemiItemsDTO dto, Integer operatorId);

    PageResult<PalletTaskPageVO> pagePalletTasks(PalletTaskQueryDTO queryDTO);

    // 单托盘入库确认（封装 DTO 后调用 stockIn/addSemiProductRecord）
    InVO confirmSingleFinishedTaskIn(ConfirmPalletInItemDTO dto, Integer operatorId);

    // 批量入库确认，任一失败整体回滚
    List<InVO> confirmFinishedTaskInBatch(ConfirmPalletInBatchDTO dto, Integer operatorId);

    // 批量作废托盘码并取消关联任务
    void invalidatePalletCodes(CancelPalletBatchDTO dto, Integer operatorId);

    // 批量取消任务（并同时作废托盘码）
    void cancelTasksByCodes(CancelPalletBatchDTO dto, Integer operatorId);
}
