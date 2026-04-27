package com.Laibin.SugarInventory.printerassistant.controller;

import com.Laibin.SugarInventory.printerassistant.common.AssistantResponse;
import com.Laibin.SugarInventory.printerassistant.model.DefaultPrinterRequest;
import com.Laibin.SugarInventory.printerassistant.model.LocalPrinterConfigView;
import com.Laibin.SugarInventory.printerassistant.model.PrintJobRequest;
import com.Laibin.SugarInventory.printerassistant.model.PrintResultView;
import com.Laibin.SugarInventory.printerassistant.model.PrinterAssistantStatusView;
import com.Laibin.SugarInventory.printerassistant.model.TestPrintRequest;
import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantFacadeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
public class PrinterAssistantController {

    private final PrinterAssistantFacadeService printerAssistantFacadeService;

    public PrinterAssistantController(PrinterAssistantFacadeService printerAssistantFacadeService) {
        this.printerAssistantFacadeService = printerAssistantFacadeService;
    }

    @GetMapping("/health")
    public AssistantResponse<Void> health() {
        return AssistantResponse.success(null, "printer-assistant-online");
    }

    @GetMapping("/printers")
    public AssistantResponse<List<String>> listPrinters() {
        return AssistantResponse.success(printerAssistantFacadeService.listPrinters(), "success");
    }

    @GetMapping("/status")
    public AssistantResponse<PrinterAssistantStatusView> getStatus() {
        return AssistantResponse.success(printerAssistantFacadeService.getStatus(true, "printer-assistant-online"), "success");
    }

    @GetMapping("/config")
    public AssistantResponse<LocalPrinterConfigView> getConfig() {
        return AssistantResponse.success(printerAssistantFacadeService.getConfig(), "success");
    }

    @PostMapping("/config/default-printer")
    public AssistantResponse<LocalPrinterConfigView> saveDefaultPrinter(@RequestBody(required = false) DefaultPrinterRequest request) {
        String printerName = request == null ? null : request.printerName();
        return AssistantResponse.success(printerAssistantFacadeService.saveDefaultPrinter(printerName), "success");
    }

    @PostMapping("/print/test")
    public AssistantResponse<PrintResultView> printTest(@Valid @RequestBody(required = false) TestPrintRequest request) {
        String printerName = request == null ? null : request.printerName();
        int copies = request == null ? 1 : request.safeCopies();
        return AssistantResponse.success(
                printerAssistantFacadeService.printTest(printerName, copies),
                "测试标签已提交到打印机"
        );
    }

    @PostMapping("/print")
    public AssistantResponse<PrintResultView> print(@Valid @RequestBody PrintJobRequest request) {
        return AssistantResponse.success(
                printerAssistantFacadeService.print(request),
                "打印任务已提交"
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AssistantResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(AssistantResponse.error(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AssistantResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(AssistantResponse.error(exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<AssistantResponse<Void>> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AssistantResponse.error(exception.getMessage()));
    }
}
