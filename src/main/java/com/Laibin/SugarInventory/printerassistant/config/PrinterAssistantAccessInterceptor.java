package com.Laibin.SugarInventory.printerassistant.config;

import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantAccessKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class PrinterAssistantAccessInterceptor implements HandlerInterceptor {

    public static final String ACCESS_KEY_HEADER = "X-Laibin-Printer-Key";

    private final PrinterAssistantAccessKeyService accessKeyService;

    public PrinterAssistantAccessInterceptor(PrinterAssistantAccessKeyService accessKeyService) {
        this.accessKeyService = accessKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (accessKeyService.matches(request.getHeader(ACCESS_KEY_HEADER))) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"打印助手连接密钥无效\",\"data\":null}"
        );
        return false;
    }
}
