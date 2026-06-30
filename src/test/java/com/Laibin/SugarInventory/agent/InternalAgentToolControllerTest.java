package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.internal.controller.InternalAgentToolController;
import com.Laibin.SugarInventory.agent.internal.controller.InternalAgentToolControllerAdvice;
import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolRequestDTO;
import com.Laibin.SugarInventory.agent.internal.service.InternalAgentToolGatewayService;
import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolErrorVO;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolResponseVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAgentToolControllerTest {
    private InternalAgentToolGatewayService gatewayService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gatewayService = mock(InternalAgentToolGatewayService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalAgentToolController(gatewayService))
                .setControllerAdvice(new InternalAgentToolControllerAdvice())
                .build();
    }

    @Test
    void exposesFixedInternalToolEndpoint() throws Exception {
        when(gatewayService.invoke(eq("service-key"), eq("resolve_products"), any(InternalAgentToolRequestDTO.class)))
                .thenReturn(InternalAgentToolResponseVO.success(
                        "resolve_products",
                        "tool_001",
                        new ObjectMapper().createObjectNode().put("resolutionStatus", "UNIQUE"),
                        "audit_001"));

        mockMvc.perform(post("/internal/agent/tools/resolve_products")
                        .header(McpInternalAgentToolGatewayService.SERVICE_KEY_HEADER, "service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentSessionId": "agt_001",
                                  "toolCallId": "tool_001",
                                  "arguments": {"query": "黄冰糖"},
                                  "client": {"traceId": "trace_001", "requestId": "req_001"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("resolve_products"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.auditRef").value("audit_001"));
    }

    @Test
    void mapsServiceAuthenticationFailureToHttp401() throws Exception {
        InternalAgentToolErrorVO error = new InternalAgentToolErrorVO(
                "SERVICE_AUTHENTICATION_FAILED",
                "内部 Agent 服务认证失败。",
                "ERROR",
                null,
                false,
                List.of("检查内部服务鉴权配置。"),
                null);
        when(gatewayService.invoke(eq(null), eq("resolve_products"), any(InternalAgentToolRequestDTO.class)))
                .thenReturn(InternalAgentToolResponseVO.error("resolve_products", "tool_001", error, null));

        mockMvc.perform(post("/internal/agent/tools/resolve_products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentSessionId": "agt_001",
                                  "toolCallId": "tool_001",
                                  "arguments": {"query": "黄冰糖"}
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SERVICE_AUTHENTICATION_FAILED"));
    }

@Test
    void rejectsUnknownTopLevelFields() throws Exception {
        mockMvc.perform(post("/internal/agent/tools/resolve_products")
                        .header(McpInternalAgentToolGatewayService.SERVICE_KEY_HEADER, "service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentSessionId": "agt_001",
                                  "toolCallId": "tool_001",
                                  "arguments": {"query": "黄冰糖"},
                                  "delegationToken": "must-not-be-accepted"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("must-not-be-accepted"))));
    }

    @Test
    void malformedJsonReturnsSafeStructuredErrorWithoutStack() throws Exception {
        mockMvc.perform(post("/internal/agent/tools/resolve_products")
                        .header(McpInternalAgentToolGatewayService.SERVICE_KEY_HEADER, "service-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ARGUMENT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("HttpMessageNotReadableException"))));
    }
}
