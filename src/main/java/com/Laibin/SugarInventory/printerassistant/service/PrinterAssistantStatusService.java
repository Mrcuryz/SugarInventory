package com.Laibin.SugarInventory.printerassistant.service;

import com.Laibin.SugarInventory.printerassistant.model.LastPrintStatusView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class PrinterAssistantStatusService {

    private final AtomicReference<LastPrintStatusView> lastPrintStatus = new AtomicReference<>();

    public LastPrintStatusView getLastPrintStatus() {
        return lastPrintStatus.get();
    }

    public void markPrintSuccess(String printerName, int printedCount, String message) {
        lastPrintStatus.set(new LastPrintStatusView(
                true,
                message,
                printerName,
                printedCount,
                LocalDateTime.now()
        ));
    }

    public void markPrintFailure(String printerName, String message) {
        lastPrintStatus.set(new LastPrintStatusView(
                false,
                message,
                printerName,
                null,
                LocalDateTime.now()
        ));
    }
}
