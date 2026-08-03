package com.Laibin.SugarInventory.inventoryhistory.task;

import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryDailyJobService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryJobResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryReconciliationCatchupTask {
    private static final Logger log =
            LoggerFactory.getLogger(InventoryReconciliationCatchupTask.class);
    private final InventoryHistoryDailyJobService dailyJobService;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileLatestClosedDay() {
        try {
            InventoryHistoryJobResult result =
                    dailyJobService.verifyLatestClosedDay("APPLICATION_STARTUP_VERIFY");
            if (!result.operationalSuccess()) {
                log.warn(
                        "启动检查发现库存日终任务缺口，businessDate={}，reason={}",
                        result.businessDate(),
                        result.message()
                );
            } else if (!result.dataQualityPassed()) {
                log.warn(
                        "启动检查发现库存趋势数据质量门禁未通过，businessDate={}，reconciliationStatus={}",
                        result.businessDate(),
                        result.reconciliationStatus()
                );
            }
        } catch (RuntimeException failure) {
            log.warn("启动检查库存日终任务失败", failure);
        }
    }
}
