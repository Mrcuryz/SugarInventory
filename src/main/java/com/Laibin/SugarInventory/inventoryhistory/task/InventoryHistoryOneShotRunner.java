package com.Laibin.SugarInventory.inventoryhistory.task;

import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryDailyJobService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryJobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@Order(Integer.MIN_VALUE)
@ConditionalOnProperty(name = "inventory.history.one-shot-mode")
public class InventoryHistoryOneShotRunner implements ApplicationRunner {
    private static final Logger log =
            LoggerFactory.getLogger(InventoryHistoryOneShotRunner.class);

    private final InventoryHistoryDailyJobService dailyJobService;
    private final InventoryHistoryProcessTerminator processTerminator;
    private final String mode;

    public InventoryHistoryOneShotRunner(
            InventoryHistoryDailyJobService dailyJobService,
            InventoryHistoryProcessTerminator processTerminator,
            @Value("${inventory.history.one-shot-mode:}") String mode
    ) {
        this.dailyJobService = dailyJobService;
        this.processTerminator = processTerminator;
        this.mode = mode;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode;
        try {
            InventoryHistoryJobResult result = execute();
            exitCode = result.exitCode();
            if (exitCode == 0) {
                log.info("库存历史一次性任务完成：{}", result.message());
            } else {
                log.error(
                        "库存历史一次性任务未通过，exitCode={}，businessDate={}，message={}",
                        exitCode,
                        result.businessDate(),
                        result.message()
                );
            }
        } catch (RuntimeException failure) {
            exitCode = 2;
            log.error("库存历史一次性任务异常退出", failure);
        }
        processTerminator.exit(exitCode);
    }

    private InventoryHistoryJobResult execute() {
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "capture" -> dailyJobService.captureLatestClosedDay("EXTERNAL_ONE_SHOT");
            case "verify" -> dailyJobService.verifyLatestClosedDay("EXTERNAL_VERIFY");
            default -> throw new IllegalArgumentException(
                    "inventory.history.one-shot-mode 仅支持 capture 或 verify"
            );
        };
    }
}
