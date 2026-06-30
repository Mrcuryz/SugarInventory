package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.python.HttpPythonAgentClient;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.runtime.AgentRuntimeProperties;
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

import static org.assertj.core.api.Assertions.assertThat;

class HttpPythonAgentClientTest {
    private HttpServer server;

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
                     "dependencies":{"toolGateway":"UP","memory":"UP","model":"BASIC_RUNTIME"}}
                    """);
        });
        server.createContext("/internal/agent/chat", exchange -> {
            chatServiceKey.set(exchange.getRequestHeaders().getFirst("X-Agent-Service-Key"));
            chatBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    {"agentSessionId":"agt_001","answer":"库存查询完成。","needsUserSelection":false,
                     "cards":[],"suggestions":[],"debug":null,"error":null}
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
        String bodyText = chatBody.get().toString();
        assertThat(bodyText).doesNotContain(
                "python-service-secret", "delegationToken", "Authorization", "refreshToken", "password");
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}