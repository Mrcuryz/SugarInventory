package com.Laibin.SugarInventory.task;

import com.Laibin.SugarInventory.service.PalletCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PalletFlowCleanupTask {
    private static final Logger log = LoggerFactory.getLogger(PalletFlowCleanupTask.class);
    private static final int RETENTION_DAYS = 180;

    private final PalletCodeService palletCodeService;

    public PalletFlowCleanupTask(PalletCodeService palletCodeService) {
        this.palletCodeService = palletCodeService;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanExpiredPalletFlows() {
        int deleted = palletCodeService.cleanExpiredPalletFlows(RETENTION_DAYS);
        log.info("Cleaned {} expired pallet flow records older than {} days", deleted, RETENTION_DAYS);
    }
}
