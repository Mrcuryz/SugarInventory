package com.Laibin.SugarInventory.mcp.client;

import com.Laibin.SugarInventory.mcp.config.WarehouseApiProperties;
import com.Laibin.SugarInventory.mcp.security.EnvironmentWarehouseTokenProvider;
import com.Laibin.SugarInventory.mcp.security.WarehouseTokenProvider;
import com.Laibin.SugarInventory.mcp.security.WarehouseToolCallContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Component
public class WarehouseApiClient {
    private static final Logger log = LoggerFactory.getLogger(WarehouseApiClient.class);

    private final String baseUrl;
    private final WarehouseTokenProvider tokenProvider;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public WarehouseApiClient(WarehouseApiProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new EnvironmentWarehouseTokenProvider(properties), HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build());
    }

    public WarehouseApiClient(WarehouseApiProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this(properties, objectMapper, new EnvironmentWarehouseTokenProvider(properties), httpClient);
    }

    public WarehouseApiClient(WarehouseApiProperties properties, ObjectMapper objectMapper, WarehouseTokenProvider tokenProvider, HttpClient httpClient) {
        this.baseUrl = trimTrailingSlash(properties.baseUrl());
        this.tokenProvider = tokenProvider;
        this.timeout = properties.timeout();
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public JsonNode getData(String path) {
        return getData(path, Map.of());
    }

    public JsonNode getData(String path, Map<String, ?> query) {
        HttpRequest.Builder builder = baseRequest(path, query).GET();
        return unwrap(exchange(builder.build()));
    }

    public JsonNode postData(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body == null ? Map.of() : body);
            HttpRequest.Builder builder = baseRequest(path, Map.of())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            return unwrap(exchange(builder.build()));
        } catch (IOException e) {
            throw new WarehouseApiException("CLIENT_SERIALIZATION_ERROR", "Unable to serialize backend request.", null, false);
        }
    }

    public <T> T postData(String path, Object body, Class<T> responseType) {
        JsonNode data = postData(path, body);
        try {
            return objectMapper.treeToValue(data, responseType);
        } catch (IOException e) {
            throw new WarehouseApiException("CLIENT_DESERIALIZATION_ERROR",
                    "Unable to deserialize backend response.", null, false);
        }
    }

    private HttpRequest.Builder baseRequest(String path, Map<String, ?> query) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(buildUri(path, query))
                .timeout(timeout)
                .header("Accept", "application/json");
        tokenProvider.currentToken().ifPresent(token -> builder.header("Authorization", "Bearer " + token));
        tokenProvider.agentSessionId().ifPresent(sessionId -> builder.header("X-Agent-Session-Id", sessionId));
        WarehouseToolCallContext.currentToolName()
                .ifPresent(toolName -> builder.header("X-Agent-Tool-Name", sanitizeHeader(toolName, 100)));
        return builder;
    }

    private URI buildUri(String path, Map<String, ?> query) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        StringJoiner params = new StringJoiner("&");
        for (Map.Entry<String, ?> entry : new LinkedHashMap<>(query).entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (text.isBlank()) {
                continue;
            }
            params.add(encode(entry.getKey()) + "=" + encode(text));
        }
        String queryString = params.toString();
        String url = baseUrl + normalizedPath + (queryString.isBlank() ? "" : "?" + queryString);
        return URI.create(url);
    }

    private JsonNode exchange(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 400) {
                logUpstreamMessage("HTTP 400", response.body());
                throw new WarehouseApiException("UPSTREAM_BAD_REQUEST", "Warehouse backend rejected the read request.", 400, false);
            }
            if (status == 401) {
                throw new WarehouseApiException("UPSTREAM_UNAUTHORIZED", "Warehouse backend authentication failed.", 401, false);
            }
            if (status == 403) {
                throw new WarehouseApiException("UPSTREAM_PERMISSION_DENIED", "Warehouse backend denied this read permission.", 403, false);
            }
            if (status == 404) {
                logUpstreamMessage("HTTP 404", response.body());
                throw new WarehouseApiException("UPSTREAM_NOT_FOUND", "Warehouse backend resource was not found.", 404, false);
            }
            if (status >= 500) {
                logUpstreamMessage("HTTP " + status, response.body());
                throw new WarehouseApiException("UPSTREAM_SERVER_ERROR", "Warehouse backend failed while serving the read request.", status, true);
            }
            if (status >= 400) {
                logUpstreamMessage("HTTP " + status, response.body());
                throw new WarehouseApiException("UPSTREAM_BAD_REQUEST", "Warehouse backend rejected the read request.", status, false);
            }
            if (response.body() == null || response.body().isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (HttpTimeoutException e) {
            throw new WarehouseApiException("UPSTREAM_TIMEOUT", "Warehouse backend request timed out.", null, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WarehouseApiException("UPSTREAM_TIMEOUT", "Warehouse backend request was interrupted.", null, true);
        } catch (IOException e) {
            throw new WarehouseApiException("UPSTREAM_SERVER_ERROR", "Unable to reach warehouse backend.", null, true);
        }
    }

    private JsonNode unwrap(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return objectMapper.nullNode();
        }
        if (root.has("code")) {
            int code = root.path("code").asInt();
            if (code != 200) {
                String upstreamMessage = root.path("msg").asText();
                logUpstreamMessage("business code " + code, upstreamMessage);
                throw new WarehouseApiException(mapBusinessCode(code), safeBusinessMessage(code), code, isRetryableBusinessCode(code));
            }
            return root.path("data");
        }
        return root;
    }

    private static String mapBusinessCode(int code) {
        return switch (code) {
            case 400 -> "UPSTREAM_BAD_REQUEST";
            case 401 -> "UPSTREAM_UNAUTHORIZED";
            case 403 -> "UPSTREAM_PERMISSION_DENIED";
            case 404 -> "UPSTREAM_NOT_FOUND";
            default -> code >= 500 ? "UPSTREAM_SERVER_ERROR" : "UPSTREAM_BUSINESS_ERROR";
        };
    }

    private static String safeBusinessMessage(int code) {
        return switch (mapBusinessCode(code)) {
            case "UPSTREAM_BAD_REQUEST" -> "Warehouse backend rejected the read request.";
            case "UPSTREAM_UNAUTHORIZED" -> "Warehouse backend authentication failed.";
            case "UPSTREAM_PERMISSION_DENIED" -> "Warehouse backend denied this read permission.";
            case "UPSTREAM_NOT_FOUND" -> "Warehouse backend resource was not found.";
            case "UPSTREAM_SERVER_ERROR" -> "Warehouse backend failed while serving the read request.";
            default -> "Warehouse backend returned a business error.";
        };
    }

    private static boolean isRetryableBusinessCode(int code) {
        return code >= 500;
    }

    private static void logUpstreamMessage(String context, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        log.warn("Warehouse backend returned {}: {}", context, sanitizeForLog(message));
    }

    private static String sanitizeForLog(String message) {
        String sanitized = message;
        sanitized = sanitized.replaceAll("(?i)authorization\\s*[:=]\\s*bearer\\s+[A-Za-z0-9._~+/=-]+", "Authorization: <redacted>");
        sanitized = sanitized.replaceAll("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer <redacted>");
        sanitized = sanitized.replaceAll("(?i)(token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "$1=<redacted>");
        sanitized = sanitized.replaceAll("(?i)jdbc:[^\\s,;]+", "jdbc:<redacted>");
        sanitized = sanitized.replaceAll("(?i)(select|insert|update|delete|drop|alter|create)\\s+[^\\r\\n;]*", "<sql redacted>");
        sanitized = sanitized.replaceAll("[A-Za-z]:\\\\[^\\r\\n\\t ]+", "<path redacted>");
        sanitized = sanitized.replaceAll("(/[A-Za-z0-9._-]+){2,}", "<path redacted>");
        sanitized = sanitized.replaceAll("(?m)^\\s*at\\s+.+$", "<stack redacted>");
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private static String sanitizeHeader(String value, int maxLength) {
        String sanitized = value.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
