package com.Laibin.SugarInventory.printerassistant.service;

import com.Laibin.SugarInventory.printerassistant.model.LastPrintStatusView;
import com.Laibin.SugarInventory.printerassistant.model.LocalPrinterConfigData;
import com.Laibin.SugarInventory.printerassistant.model.LocalPrinterConfigView;
import com.Laibin.SugarInventory.printerassistant.model.PrintJobRequest;
import com.Laibin.SugarInventory.printerassistant.model.PrintLabelRequest;
import com.Laibin.SugarInventory.printerassistant.model.PrintResultView;
import com.Laibin.SugarInventory.printerassistant.model.PrinterAssistantStatusView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.awt.image.BufferedImage;
import java.util.List;

@Service
public class PrinterAssistantFacadeService {

    private static final String LOCAL_ADDRESS = "127.0.0.1:9527";

    private final PrinterDiscoveryService printerDiscoveryService;
    private final LocalPrinterConfigService localPrinterConfigService;
    private final LabelRenderService labelRenderService;
    private final PrintExecutionService printExecutionService;
    private final PrinterAssistantStatusService printerAssistantStatusService;
    private final LaunchOnStartupService launchOnStartupService;

    public PrinterAssistantFacadeService(
            PrinterDiscoveryService printerDiscoveryService,
            LocalPrinterConfigService localPrinterConfigService,
            LabelRenderService labelRenderService,
            PrintExecutionService printExecutionService,
            PrinterAssistantStatusService printerAssistantStatusService,
            LaunchOnStartupService launchOnStartupService
    ) {
        this.printerDiscoveryService = printerDiscoveryService;
        this.localPrinterConfigService = localPrinterConfigService;
        this.labelRenderService = labelRenderService;
        this.printExecutionService = printExecutionService;
        this.printerAssistantStatusService = printerAssistantStatusService;
        this.launchOnStartupService = launchOnStartupService;
    }

    public List<String> listPrinters() {
        return printerDiscoveryService.listPrinterNames();
    }

    public LocalPrinterConfigView getConfig() {
        LocalPrinterConfigData config = localPrinterConfigService.load();
        return new LocalPrinterConfigView(
                config.defaultPrinterName(),
                printerDiscoveryService.getSystemDefaultPrinterName()
        );
    }

    public LocalPrinterConfigView saveDefaultPrinter(String printerName) {
        printerDiscoveryService.validatePrinterName(printerName);
        localPrinterConfigService.saveDefaultPrinter(printerName);
        return getConfig();
    }

    public PrintResultView printTest(String printerName, int copies) {
        String targetPrinter = resolvePrinterName(printerName);
        try {
            BufferedImage image = labelRenderService.renderTestLabel();
            PrintExecutionService.PrintResult result = printExecutionService.print(List.of(image), printerName, copies);
            printerAssistantStatusService.markPrintSuccess(
                    result.printerName(),
                    result.printedCount(),
                    "测试标签已提交到打印机"
            );
            return new PrintResultView(result.printerName(), result.printedCount());
        } catch (RuntimeException exception) {
            printerAssistantStatusService.markPrintFailure(targetPrinter, exception.getMessage());
            throw exception;
        }
    }

    public PrintResultView print(PrintJobRequest request) {
        String targetPrinter = resolvePrinterName(request.printerName());
        try {
            List<BufferedImage> labelImages = labelRenderService.renderLabels(request.template(), request.labels());
            PrintExecutionService.PrintResult result = printExecutionService.print(
                    labelImages,
                    request.printerName(),
                    request.safeCopies()
            );
            printerAssistantStatusService.markPrintSuccess(
                    result.printerName(),
                    result.printedCount(),
                    "打印任务已提交"
            );
            return new PrintResultView(result.printerName(), result.printedCount());
        } catch (RuntimeException exception) {
            printerAssistantStatusService.markPrintFailure(targetPrinter, exception.getMessage());
            throw exception;
        }
    }

    public PrinterAssistantStatusView getStatus(boolean running, String message) {
        LocalPrinterConfigData config = localPrinterConfigService.load();
        LastPrintStatusView lastPrintStatus = printerAssistantStatusService.getLastPrintStatus();
        boolean launchOnStartup = launchOnStartupService.isEnabled() || config.safeLaunchOnStartup();
        return new PrinterAssistantStatusView(
                running,
                LOCAL_ADDRESS,
                message,
                PrinterAssistantPaths.getLogFilePath().toString(),
                launchOnStartup,
                lastPrintStatus
        );
    }

    public boolean isLaunchOnStartupEnabled() {
        return launchOnStartupService.isEnabled();
    }

    public boolean isLaunchOnStartupSupported() {
        return launchOnStartupService.isSupported();
    }

    public void setLaunchOnStartup(boolean enabled) {
        launchOnStartupService.setEnabled(enabled);
        LocalPrinterConfigData current = localPrinterConfigService.load();
        localPrinterConfigService.save(current.defaultPrinterName(), enabled);
    }

    private String resolvePrinterName(String printerName) {
        if (StringUtils.hasText(printerName)) {
            return printerName.trim();
        }
        LocalPrinterConfigData config = localPrinterConfigService.load();
        if (StringUtils.hasText(config.defaultPrinterName())) {
            return config.defaultPrinterName().trim();
        }
        return printerDiscoveryService.getSystemDefaultPrinterName();
    }
}
