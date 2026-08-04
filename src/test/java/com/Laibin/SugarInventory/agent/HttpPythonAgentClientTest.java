package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.python.HttpPythonAgentClient;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentStreamEventDTO;
import com.Laibin.SugarInventory.agent.runtime.AgentRuntimeProperties;
import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPythonAgentClientTest {
    private HttpServer server;

    @Test
    void defaultJavaTimeoutExceedsPythonLlmRunBudget() {
        assertThat(new AgentRuntimeProperties().getPythonTimeoutMs()).isEqualTo(100000);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsServiceKeyOnlyAsInternalHeaderAndCallsExpectedPaths() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> serviceKey = new AtomicReference<>();
        AtomicReference<JsonNode> chatBody = new AtomicReference<>();
        AtomicReference<String> chatServiceKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/health", exchange -> {
            serviceKey.set(exchange.getRequestHeaders().getFirst("X-Agent-Service-Key"));
            respond(exchange, 200, """
                    {"status":"UP","service":"warehouse-agent-service","version":"0.1.0",
                     "dependencies":{"toolGateway":"UP","memory":"UP","model":"BASIC"}}
                    """);
        });
        server.createContext("/internal/agent/capabilities", exchange -> respond(exchange, 200, expectedCapabilities()));
        server.createContext("/internal/agent/chat", exchange -> {
            chatServiceKey.set(exchange.getRequestHeaders().getFirst("X-Agent-Service-Key"));
            chatBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    {"agentSessionId":"agt_001","answer":"库存查询完成。","needsUserSelection":false,
                     "cards":[],"suggestions":[],
                     "reviewTrace":{"knowledgeAudit":{"targetAgent":"knowledge_expert","status":"SUCCEEDED"}},
                     "debug":null,"error":null}
                    """);
        });
        server.start();

        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setPythonBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPythonTimeoutMs(2000);
        properties.setPythonServiceKey("python-service-secret");
        HttpPythonAgentClient client = new HttpPythonAgentClient(properties, objectMapper);

        assertThat(client.isHealthy()).isTrue();
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO();
        request.setAgentSessionId("agt_001");
        PythonAgentChatRequestDTO.Message message = new PythonAgentChatRequestDTO.Message();
        message.setType("user_message");
        message.setContent("查黄冰糖库存");
        request.setMessage(message);
        PythonAgentChatResponseDTO response = client.chat(request);

        assertThat(serviceKey).hasValue("python-service-secret");
        assertThat(chatServiceKey).hasValue("python-service-secret");
        assertThat(response.getAnswer()).isEqualTo("库存查询完成。");
        assertThat(response.getReviewTrace().path("knowledgeAudit").path("targetAgent").asText())
                .isEqualTo("knowledge_expert");
        String bodyText = chatBody.get().toString();
        assertThat(bodyText).doesNotContain(
                "python-service-secret", "delegationToken", "Authorization", "refreshToken", "password");
    }

    @Test
    void failsClosedWhenCapabilityRegistryDoesNotMatch() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/health", exchange -> respond(exchange, 200,
                "{\"status\":\"UP\",\"dependencies\":{\"toolGateway\":\"UP\"}}"));
        server.createContext("/internal/agent/capabilities", exchange -> respond(exchange, 200,
                expectedCapabilities().replace("\"toolCount\":53", "\"toolCount\":52")));
        server.start();

        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setPythonBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPythonTimeoutMs(2000);
        properties.setPythonServiceKey("python-service-secret");

        assertThat(new HttpPythonAgentClient(properties, objectMapper).isHealthy()).isFalse();
    }

    @Test
    void failsClosedWhenExpertProfileRegistryDoesNotMatch() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/health", exchange -> respond(exchange, 200,
                "{\"status\":\"UP\",\"dependencies\":{\"toolGateway\":\"UP\"}}"));
        server.createContext("/internal/agent/capabilities", exchange -> respond(exchange, 200,
                expectedCapabilities().replace(
                        "b7d1c5ddb13729d5488032b6386be65a8cc92e9f94ffe5fd392567d540e7b430",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")));
        server.start();

        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setPythonBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPythonTimeoutMs(2000);
        properties.setPythonServiceKey("python-service-secret");

        assertThat(new HttpPythonAgentClient(properties, objectMapper).isHealthy()).isFalse();
    }

    @Test
    void javaExpertProfileRegistryMatchesThePythonCanonicalHash() {
        assertThat(McpInternalAgentToolGatewayService.agentProfileRegistryHash(new ObjectMapper()))
                .isEqualTo("b7d1c5ddb13729d5488032b6386be65a8cc92e9f94ffe5fd392567d540e7b430");
    }

    @Test
    void parsesStreamEventsFromPythonSseEndpoint() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> streamServiceKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/chat/stream", exchange -> {
            streamServiceKey.set(exchange.getRequestHeaders().getFirst("X-Agent-Service-Key"));
            String body = """
                    event: message_start
                    data: {"eventId":"evt_000001","messageId":"msg_001","agentSessionId":"agt_001","type":"message_start","sequence":1,"payload":{"role":"assistant"}}

                    event: message_end
                    data: {"eventId":"evt_000002","messageId":"msg_001","agentSessionId":"agt_001","type":"message_end","sequence":2,"payload":{"finishReason":"completed"}}

                    """;
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setPythonBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPythonTimeoutMs(2000);
        properties.setPythonServiceKey("python-service-secret");
        HttpPythonAgentClient client = new HttpPythonAgentClient(properties, objectMapper);
        PythonAgentChatRequestDTO request = new PythonAgentChatRequestDTO();
        request.setAgentSessionId("agt_001");
        PythonAgentChatRequestDTO.Message message = new PythonAgentChatRequestDTO.Message();
        message.setType("user_message");
        message.setContent("查黄冰糖库存");
        request.setMessage(message);
        List<PythonAgentStreamEventDTO> events = new ArrayList<>();

        client.stream(request, events::add);

        assertThat(streamServiceKey).hasValue("python-service-secret");
        assertThat(events).extracting(PythonAgentStreamEventDTO::getType)
                .containsExactly("message_start", "message_end");
        assertThat(events.get(1).getPayload().path("finishReason").asText()).isEqualTo("completed");
    }

    @Test
    void sendsCancellationWithServiceKeyAndMessageIdentity() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> serviceKey = new AtomicReference<>();
        AtomicReference<JsonNode> cancelBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/cancel", exchange -> {
            serviceKey.set(exchange.getRequestHeaders().getFirst("X-Agent-Service-Key"));
            cancelBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "{\"agentSessionId\":\"agt_001\",\"messageId\":\"msg_001\",\"cancelled\":true}");
        });
        server.start();

        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setPythonBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPythonTimeoutMs(2000);
        properties.setPythonServiceKey("python-service-secret");
        HttpPythonAgentClient client = new HttpPythonAgentClient(properties, objectMapper);

        client.cancel("agt_001", "msg_001");

        assertThat(serviceKey).hasValue("python-service-secret");
        assertThat(cancelBody.get().path("agentSessionId").asText()).isEqualTo("agt_001");
        assertThat(cancelBody.get().path("messageId").asText()).isEqualTo("msg_001");
        assertThat(cancelBody.get().toString()).doesNotContain("python-service-secret");
    }

    @Test
    void clearsPythonSessionWithServiceKeyAndDeleteMethod() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<String> serviceKey = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/agent/sessions/agt_001", exchange -> {
            serviceKey.set(exchange.getRequestHeaders().getFirst("X-Agent-Service-Key"));
            method.set(exchange.getRequestMethod());
            respond(exchange, 200, "{\"agentSessionId\":\"agt_001\",\"cleared\":true}");
        });
        server.start();

        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setPythonBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPythonTimeoutMs(2000);
        properties.setPythonServiceKey("python-service-secret");
        HttpPythonAgentClient client = new HttpPythonAgentClient(properties, objectMapper);

        client.clearSession("agt_001");

        assertThat(serviceKey).hasValue("python-service-secret");
        assertThat(method).hasValue("DELETE");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String expectedCapabilities() {
        return """
                {"runtimeVersion":"0.2.0","protocolVersion":"1.0",
                 "toolRegistryHash":"2e619137e5c3f8dd15bd3c35fa57bcfa241902ca75bec6d46da31603bcfbe4de",
                 "recipeRegistryHash":"c5ee0907e4134024138ff5489bd9b58d92c4103663a00f0d0d14fa5a40d12385",
                 "agentProfileRegistryHash":"b7d1c5ddb13729d5488032b6386be65a8cc92e9f94ffe5fd392567d540e7b430",
                 "toolCount":53,"recipeCount":1,"agentProfiles":[]}
                """;
    }
}
