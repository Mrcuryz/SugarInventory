package com.Laibin.SugarInventory.printerassistant.config;

import com.Laibin.SugarInventory.printerassistant.service.PrinterAssistantAccessKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrinterAssistantAccessInterceptorTest {

    private final PrinterAssistantAccessKeyService accessKeyService = mock(PrinterAssistantAccessKeyService.class);
    private final PrinterAssistantAccessInterceptor interceptor = new PrinterAssistantAccessInterceptor(accessKeyService);

    @Test
    void rejectsMissingAccessKeyWithoutDisclosingProtectedData() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/printers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(accessKeyService.matches(null)).thenReturn(false);

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("连接密钥无效");
        assertThat(response.getContentAsString()).doesNotContain("access-key");
    }

    @Test
    void acceptsMatchingAccessKeyAndAllowsCorsPreflight() throws Exception {
        MockHttpServletRequest authenticated = new MockHttpServletRequest("POST", "/print");
        authenticated.addHeader(PrinterAssistantAccessInterceptor.ACCESS_KEY_HEADER, "valid-key");
        when(accessKeyService.matches("valid-key")).thenReturn(true);

        assertThat(interceptor.preHandle(authenticated, new MockHttpServletResponse(), new Object())).isTrue();

        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/print");
        assertThat(interceptor.preHandle(preflight, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
