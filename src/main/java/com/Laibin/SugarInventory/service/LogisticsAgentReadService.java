package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.PalletTaskAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletTasksAgentVO;
import com.Laibin.SugarInventory.domain.dto.StockDocumentAgentQueryDTO;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.StockDocumentsAgentVO;
import com.Laibin.SugarInventory.domain.dto.AutoInboundBatchesAgentQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AutoInboundBatchDetailAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchesAgentVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchDetailAgentVO;
import com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.TaskTransitionPreviewVO;

public interface LogisticsAgentReadService {
    PalletTasksAgentVO queryPalletTasks(PalletTaskAgentQueryDTO query);

    StockDocumentsAgentVO queryStockDocuments(StockDocumentAgentQueryDTO query, User user);

    AutoInboundBatchesAgentVO queryAutoInboundBatches(AutoInboundBatchesAgentQueryDTO query, User user);

    AutoInboundBatchDetailAgentVO getAutoInboundBatchDetail(AutoInboundBatchDetailAgentQueryDTO query, User user);

    TaskTransitionPreviewVO previewTaskTransition(TaskTransitionPreviewDTO request, User user);
}
