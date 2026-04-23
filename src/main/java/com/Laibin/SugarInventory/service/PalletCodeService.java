package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.BindTaskSemiItemsDTO;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmTransferBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmFinishOutBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiConsumeBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiOutBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiPrepareBatchDTO;
import com.Laibin.SugarInventory.domain.dto.CreateFinishOutTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateSemiOutTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateSemiPrepareTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateTransferTaskDTO;
import com.Laibin.SugarInventory.domain.dto.DeletePalletFlowBatchDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductActivateDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductBindDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductPoolQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletQrExportDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMapBatchOperationDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMapSlotInboundDTO;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.PalletAssayVO;
import com.Laibin.SugarInventory.domain.vo.PalletBindResultVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodeInfoVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowCyclePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowDetailVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.TaskSemiItemVO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolVO;
import com.Laibin.SugarInventory.domain.vo.WarehouseMapTaskCreateResultVO;
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

    byte[] generateQrCodePng(String code);

    String generateQrCodeSvg(String code);

    byte[] generateQrLabelPdf(PalletQrExportDTO dto);

    int bindFixedProductCodes(FixedProductBindDTO dto, Integer userId);

    PageResult<FixedProductQrPoolVO> pageFixedProductPool(FixedProductPoolQueryDTO queryDTO);

    byte[] generateFixedProductQrLabelPdf(PalletQrExportDTO dto);

    byte[] activateFixedProductCodesAndGeneratePdf(FixedProductActivateDTO dto, Integer userId);

    PageResult<PalletCodePageVO> pagePalletCodes(PalletCodeQueryDTO queryDTO);

    PalletAssayVO getAssayByCode(String code);

    PalletInventoryVO getInventoryByCode(String code);

    PalletBindResultVO bindPalletAndCreateTask(BindPalletTaskDTO dto, Integer operatorId);

    List<TaskSemiItemVO> bindSemiItemsToTask(BindTaskSemiItemsDTO dto, Integer operatorId);

    PageResult<PalletTaskPageVO> pagePalletTasks(PalletTaskQueryDTO queryDTO);

    PageResult<PalletFlowCyclePageVO> pagePalletFlowCycles(String code, Long pageNum, Long pageSize);

    List<PalletFlowDetailVO> listPalletFlowsByCycle(String code, Integer cycleNo);

    void deletePalletFlows(DeletePalletFlowBatchDTO dto);

    int cleanExpiredPalletFlows(int retentionDays);

    // 单托盘入库确认（封装 DTO 后调用 stockIn/addSemiProductRecord）
    InVO confirmSingleFinishedTaskIn(ConfirmPalletInItemDTO dto, Integer operatorId);

    // 批量入库确认，任一失败整体回滚
    List<InVO> confirmFinishedTaskInBatch(ConfirmPalletInBatchDTO dto, Integer operatorId);

    // 批量作废空闲托盘码
    void invalidatePalletCodes(CancelPalletBatchDTO dto, Integer operatorId);

    void restoreInvalidPalletCodes(CancelPalletBatchDTO dto, Integer operatorId);

    // 批量取消当前轮次待处理任务，并释放托盘回 FREE
    void cancelTasksByCodes(CancelPalletBatchDTO dto, Integer operatorId);

    void createSemiOutTasks(CreateSemiOutTaskDTO dto, Integer operatorId);

    void confirmSemiOutTasks(ConfirmSemiOutBatchDTO dto, Integer operatorId);

    void createSemiPrepareTasks(CreateSemiPrepareTaskDTO dto, Integer operatorId);

    void confirmSemiPrepareTasks(ConfirmSemiPrepareBatchDTO dto, Integer operatorId);

    void confirmSemiConsume(ConfirmSemiConsumeBatchDTO dto, Integer operatorId);

    void createFinishOutTasks(CreateFinishOutTaskDTO dto, Integer operatorId);

    void confirmFinishOutTasks(ConfirmFinishOutBatchDTO dto, Integer operatorId);

    void createTransferTasks(CreateTransferTaskDTO dto, Integer operatorId);

    void confirmTransferTasks(ConfirmTransferBatchDTO dto, Integer operatorId);

    WarehouseMapTaskCreateResultVO createWarehouseMapTasks(WarehouseMapBatchOperationDTO dto, Integer operatorId);

    InVO createWarehouseMapSlotInbound(WarehouseMapSlotInboundDTO dto, Integer operatorId);
}
