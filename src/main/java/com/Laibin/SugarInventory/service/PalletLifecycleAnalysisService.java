package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.PalletAnomaliesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletFlowRecordsQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletLifecycleQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PrintedNotInboundCodesQueryDTO;
import com.Laibin.SugarInventory.domain.dto.QrBatchInboundCompletionQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletAnomaliesVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowRecordsVO;
import com.Laibin.SugarInventory.domain.vo.PalletLifecycleVO;
import com.Laibin.SugarInventory.domain.vo.PrintedNotInboundCodesVO;
import com.Laibin.SugarInventory.domain.vo.QrBatchInboundCompletionVO;

public interface PalletLifecycleAnalysisService {
    PalletLifecycleVO queryQrCodeLifecycle(PalletLifecycleQueryDTO query);

    PalletFlowRecordsVO queryPalletFlowRecords(PalletFlowRecordsQueryDTO query);

    PrintedNotInboundCodesVO queryPrintedNotInboundCodes(PrintedNotInboundCodesQueryDTO query);

    PalletAnomaliesVO queryPalletAnomalies(PalletAnomaliesQueryDTO query);

    QrBatchInboundCompletionVO queryQrBatchInboundCompletion(QrBatchInboundCompletionQueryDTO query);
}
