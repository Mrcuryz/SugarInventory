package com.Laibin.SugarInventory.printerassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PrinterAssistantCorsConfig implements WebMvcConfigurer {

    private final PrinterAssistantAccessInterceptor accessInterceptor;

    public PrinterAssistantCorsConfig(PrinterAssistantAccessInterceptor accessInterceptor) {
        this.accessInterceptor = accessInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", PrinterAssistantAccessInterceptor.ACCESS_KEY_HEADER)
                .maxAge(600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/health", "/error");
    }
}
