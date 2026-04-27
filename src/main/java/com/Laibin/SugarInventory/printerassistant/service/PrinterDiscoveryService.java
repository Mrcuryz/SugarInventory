package com.Laibin.SugarInventory.printerassistant.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class PrinterDiscoveryService {

    private final LocalPrinterConfigService localPrinterConfigService;

    public PrinterDiscoveryService(LocalPrinterConfigService localPrinterConfigService) {
        this.localPrinterConfigService = localPrinterConfigService;
    }

    public List<String> listPrinterNames() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public String getSystemDefaultPrinterName() {
        PrintService printService = PrintServiceLookup.lookupDefaultPrintService();
        return printService == null ? null : printService.getName();
    }

    public PrintService resolvePrinter(String requestedPrinterName) {
        String candidateName = StringUtils.hasText(requestedPrinterName)
                ? requestedPrinterName.trim()
                : localPrinterConfigService.load().defaultPrinterName();

        if (StringUtils.hasText(candidateName)) {
            return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                    .filter(item -> item.getName().equals(candidateName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未找到打印机 " + candidateName));
        }

        PrintService defaultPrinter = PrintServiceLookup.lookupDefaultPrintService();
        if (defaultPrinter != null) {
            return defaultPrinter;
        }

        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        if (services.length == 0) {
            throw new IllegalStateException("当前电脑未检测到可用打印机");
        }
        return services[0];
    }

    public void validatePrinterName(String printerName) {
        if (!StringUtils.hasText(printerName)) {
            return;
        }
        resolvePrinter(printerName);
    }
}
