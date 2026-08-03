package com.Laibin.SugarInventory.inventoryhistory.task;

import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryDailyJobService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryJobResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryDailySnapshotTask {
    private static final Logger log = LoggerFactory.getLogger(InventoryDailySnapshotTask.class);

    private final InventoryHistoryDailyJobService dailyJobService;

    @Scheduled(cron = "${inventory.history.daily-snapshot-cron:0 0 0 * * ?}", zone = "Asia/Shanghai")
    public void captureDailyClose() {
        InventoryHistoryJobResult result =
                dailyJobService.captureLatestClosedDay("IN_PROCESS_SCHEDULED");
        if (!result.operationalSuccess()) {
            log.error(
                    "库存日终快照任务失败，businessDate={}，reason={}",
                    result.businessDate(),
                    result.message()
            );
        } else if (!result.dataQualityPassed()) {
            log.warn(
                    "库存日终任务已执行，但数据质量门禁未通过，businessDate={}，reconciliationStatus={}",
                    result.businessDate(),
                    result.reconciliationStatus()
            );
        }
    }
}
