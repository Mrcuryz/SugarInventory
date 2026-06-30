package com.Laibin.SugarInventory.agent.python;

import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.runtime.AgentRuntimeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpPythonAgentClient implements PythonAgentClient {
    static final String SERVICE_KEY_HEADER = "X-Agent-Service-Key";

    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpPythonAgentClient(AgentRuntimeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout())
                .build();
    }

    @Override
    public boolean isHealthy() {
        if (!configured()) {
            return false;
        }
        try {
            HttpRequest request = baseRequest("/internal/agent/health").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode body = objectMapper.readTree(response.body());
            return "UP".equals(body.path("status").asText())
                    && !"MISCONFIGURED".equals(body.path("dependencies").path("toolGateway").asText());
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public PythonAgentChatResponseDTO chat(PythonAgentChatRequestDTO request) {
        if (!configured()) {
            throw new PythonAgentClientException("PYTHON_AGENT_MISCONFIGURED", false);
        }
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = baseRequest("/internal/agent/chat")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new PythonAgentClientException("PYTHON_AGENT_SERVICE_UNAUTHORIZED", false);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PythonAgentClientException("PYTHON_AGENT_SERVICE_ERROR", response.statusCode() >= 500);
            }
            return objectMapper.readValue(response.body(), PythonAgentChatResponseDTO.class);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new PythonAgentClientException("PYTHON_AGENT_TIMEOUT", true);
        } catch (IOException e) {
            throw new PythonAgentClientException("PYTHON_AGENT_UNAVAILABLE", true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PythonAgentClientException("PYTHON_AGENT_INTERRUPTED", true);
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder(resolve(path))
                .timeout(timeout())
                .header("Accept", "application/json")
                .header(SERVICE_KEY_HEADER, properties.getPythonServiceKey());
    }

    private URI resolve(String path) {
        String baseUrl = properties.getPythonBaseUrl();
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalized + path);
    }

    private Duration timeout() {
        return Duration.ofMillis(Math.max(100, properties.getPythonTimeoutMs()));
    }

    private boolean configured() {
        return properties.getPythonBaseUrl() != null
                && !properties.getPythonBaseUrl().isBlank()
                && properties.getPythonServiceKey() != null
                && !properties.getPythonServiceKey().isBlank();
    }
}