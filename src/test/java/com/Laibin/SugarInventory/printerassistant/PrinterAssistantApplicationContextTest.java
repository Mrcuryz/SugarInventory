package com.Laibin.SugarInventory.printerassistant;

import com.Laibin.SugarInventory.printerassistant.config.PrinterAssistantAccessInterceptor;
import com.Laibin.SugarInventory.printerassistant.controller.PrinterAssistantController;
import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantAccessKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PrinterAssistantApplicationContextTest {

    @Test
    void standaloneContextCreatesProtectedHttpBoundary() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PrinterAssistantApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--printer-assistant.access-key=printer-assistant-context-test-key"
                )) {
            assertThat(context.getBean(PrinterAssistantController.class)).isNotNull();
            assertThat(context.getBean(PrinterAssistantAccessInterceptor.class)).isNotNull();
            assertThat(context.getBean(PrinterAssistantAccessKeyService.class).getAccessKey())
                    .isEqualTo("printer-assistant-context-test-key");
        }
    }
}
