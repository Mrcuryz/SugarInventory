package com.Laibin.SugarInventory.inventoryhistory;

import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryDailyJobService;
import com.Laibin.SugarInventory.inventoryhistory.service.InventoryHistoryJobResult;
import com.Laibin.SugarInventory.inventoryhistory.task.InventoryHistoryOneShotRunner;
import com.Laibin.SugarInventory.inventoryhistory.task.InventoryHistoryProcessTerminator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryHistoryOneShotRunnerTest {
    @Test
    void captureModePropagatesSuccessfulExitCode() {
        InventoryHistoryDailyJobService service = mock(InventoryHistoryDailyJobService.class);
        InventoryHistoryProcessTerminator terminator =
                mock(InventoryHistoryProcessTerminator.class);
        when(service.captureLatestClosedDay("EXTERNAL_ONE_SHOT"))
                .thenReturn(result(true, true));
        InventoryHistoryOneShotRunner runner =
                new InventoryHistoryOneShotRunner(service, terminator, "capture");

        runner.run(mock(ApplicationArguments.class));

        verify(terminator).exit(0);
    }

    @Test
    void verifyModeUsesDataQualityBlockedExitCode() {
        InventoryHistoryDailyJobService service = mock(InventoryHistoryDailyJobService.class);
        InventoryHistoryProcessTerminator terminator =
                mock(InventoryHistoryProcessTerminator.class);
        when(service.verifyLatestClosedDay("EXTERNAL_VERIFY"))
                .thenReturn(result(true, false));
        InventoryHistoryOneShotRunner runner =
                new InventoryHistoryOneShotRunner(service, terminator, "verify");

        runner.run(mock(ApplicationArguments.class));

        verify(terminator).exit(3);
    }

    @Test
    void unsupportedModeReturnsTechnicalFailureExitCode() {
        InventoryHistoryDailyJobService service = mock(InventoryHistoryDailyJobService.class);
        InventoryHistoryProcessTerminator terminator =
                mock(InventoryHistoryProcessTerminator.class);
        InventoryHistoryOneShotRunner runner =
                new InventoryHistoryOneShotRunner(service, terminator, "unknown");

        runner.run(mock(ApplicationArguments.class));

        verify(terminator).exit(2);
    }

    private InventoryHistoryJobResult result(boolean operation, boolean quality) {
        return new InventoryHistoryJobResult(
                LocalDate.of(2026, 7, 31),
                "TEST",
                operation,
                quality,
                false,
                "snap_1",
                "recon_1",
                quality ? "PASSED" : "FAILED",
                "test"
        );
    }
}
