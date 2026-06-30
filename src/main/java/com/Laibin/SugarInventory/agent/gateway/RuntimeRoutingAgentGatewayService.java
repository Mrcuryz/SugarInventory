package com.Laibin.SugarInventory.agent.gateway;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.python.PythonAgentClient;
import com.Laibin.SugarInventory.agent.python.PythonAgentClientException;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.runtime.AgentRuntimeProperties;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentBusinessCardVO;
import com.Laibin.SugarInventory.agent.vo.AgentChoiceOptionVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Primary
@Service
public class RuntimeRoutingAgentGatewayService implements AgentGatewayService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeRoutingAgentGatewayService.class);
    private static final String UNAVAILABLE_MESSAGE = "AI 助手暂时不可用，请稍后重试。";
    private static final Set<String> SAFE_PAGE_CONTEXT_KEYS = Set.of("path", "routeName", "pageTitle");
    private static final Pattern INTERNAL_TEXT = Pattern.compile(
            "(?i)(authorization|bearer\\s+|delegationToken|refreshToken|productId|warehouseId|toolName|stackTrace|jdbc:|\\bSUCCESS\\b|\\btoken\\b)");
    private static final Pattern LABEL_ID = Pattern.compile("\\s*[(（]#\\d+[)）]\\s*");

    private final AgentSessionService agentSessionService;
    private final AgentGatewayService legacyGateway;
    private final PythonAgentClient pythonAgentClient;
    private final AgentRuntimeProperties properties;
    private final Set<String> pythonContextSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> legacyFallbackSessions = ConcurrentHashMap.newKeySet();

    public RuntimeRoutingAgentGatewayService(
            AgentSessionService agentSessionService,
            @Qualifier("legacyAgentGatewayService") AgentGatewayService legacyGateway,
            PythonAgentClient pythonAgentClient,
            AgentRuntimeProperties properties) {
        this.agentSessionService = agentSessionService;
        this.legacyGateway = legacyGateway;
        this.pythonAgentClient = pythonAgentClient;
        this.properties = properties;
    }

    @Override
    public AgentMessageResponseVO handleMessage(LoginUser loginUser, String agentSessionId, AgentMessageRequestDTO request) {
        if (properties.getMode() == AgentRuntimeProperties.Mode.LEGACY
                || legacyFallbackSessions.contains(agentSessionId)) {
            long legacyStartedAt = System.nanoTime();
            String requestId = UUID.randomUUID().toString();
            String traceId = UUID.randomUUID().toString();
            AgentMessageResponseVO legacyResponse = sanitizeLegacyResponse(
                    legacyGateway.handleMessage(loginUser, agentSessionId, request), loginUser);
            Integer userId = loginUser == null || loginUser.getUser() == null
                    ? null : loginUser.getUser().getId();
            recordRuntimeAudit(agentSessionId, userId, requestId, traceId, request,
                    "legacy", "SUCCESS", null, legacyFallbackSessions.contains(agentSessionId),
                    legacyResponse, elapsedMillis(legacyStartedAt));
            return legacyResponse;
        }

        AgentSession session = agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentSessionVO sessionVO = agentSessionService.toSessionVO(session, loginUser);
        String requestId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        boolean fallbackUsed = false;
        String resultCode = "SUCCESS";
        String errorCode = null;
        String path = "python";
        AgentMessageResponseVO result;

        try {
            if (!pythonAgentClient.isHealthy()) {
                throw new PythonAgentClientException("PYTHON_AGENT_UNAVAILABLE", true);
            }
            PythonAgentChatRequestDTO pythonRequest = buildPythonRequest(
                    loginUser, sessionVO, request, requestId, traceId);
            PythonAgentChatResponseDTO pythonResponse = pythonAgentClient.chat(pythonRequest);
            pythonContextSessions.add(agentSessionId);
            result = mapPythonResponse(pythonResponse, sessionVO, loginUser);
            if (pythonResponse.getError() != null) {
                resultCode = "ERROR";
                errorCode = safeCode(pythonResponse.getError().getCode());
            }
        } catch (PythonAgentClientException e) {
            resultCode = "ERROR";
            errorCode = safeCode(e.getCode());
            if (canFallback(agentSessionId, request)) {
                fallbackUsed = true;
                path = "legacy";
                legacyFallbackSessions.add(agentSessionId);
                result = sanitizeLegacyResponse(legacyGateway.handleMessage(loginUser, agentSessionId, request), loginUser);
                resultCode = "FALLBACK_SUCCESS";
            } else {
                result = unavailableResponse(sessionVO);
            }
        } catch (RuntimeException e) {
            resultCode = "ERROR";
            errorCode = "PYTHON_AGENT_SERVICE_ERROR";
            result = unavailableResponse(sessionVO);
        }

        recordRuntimeAudit(agentSessionId, sessionVO.getUserId(), requestId, traceId, request,
                path, resultCode, errorCode, fallbackUsed, result, elapsedMillis(startedAt));
        return result;
    }

    @Override
    public void clearSession(String agentSessionId) {
        pythonContextSessions.remove(agentSessionId);
        legacyFallbackSessions.remove(agentSessionId);
        legacyGateway.clearSession(agentSessionId);
    }

    private PythonAgentChatRequestDTO buildPythonRequest(LoginUser loginUser,
                                                         AgentSessionVO session,
                                                         AgentMessageRequestDTO request,
                                                         String requestId,
                                                         String traceId) {
        PythonAgentChatRequestDTO payload = new PythonAgentChatRequestDTO();
        payload.setAgentSessionId(session.getAgentSessionId());

        PythonAgentChatRequestDTO.UserSummary user = new PythonAgentChatRequestDTO.UserSummary();
        user.setUserId(session.getUserId());
        user.setName(safeText(session.getName(), null));
        user.setRoleCode(safeText(session.getRoleCode(), null));
        user.setPermissionCodes(loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(this::isSafeScalar)
                .toList());
        payload.setUser(user);
        payload.setScopes(session.getScopes() == null ? List.of() : session.getScopes().stream()
                .filter(this::isSafeScalar)
                .toList());

        PythonAgentChatRequestDTO.Message message = new PythonAgentChatRequestDTO.Message();
        Map<String, Object> selectedOption = selectedOption(request.getPageContext());
        if (selectedOption == null) {
            message.setType("user_message");
            message.setContent(request.getMessage().trim());
        } else {
            message.setType("candidate_selected");
            PythonAgentChatRequestDTO.Selection selection = new PythonAgentChatRequestDTO.Selection();
            selection.setOptionId(safeScalar(selectedOption.get("optionId")));
            selection.setOptionType(safeScalar(selectedOption.get("optionType")));
            selection.setDisplayLabel(safeLabel(firstNonBlank(
                    safeScalar(selectedOption.get("displayLabel")),
                    safeScalar(selectedOption.get("rawDisplayLabel")))));
            message.setSelection(selection);
        }
        payload.setMessage(message);
        payload.setPageContext(safePageContext(request.getPageContext()));

        PythonAgentChatRequestDTO.ClientContext client = new PythonAgentChatRequestDTO.ClientContext();
        client.setRequestId(requestId);
        client.setTraceId(traceId);
        client.setDebug(isAdmin(loginUser) && Boolean.TRUE.equals(value(request.getPageContext(), "debug")));
        payload.setClient(client);
        return payload;
    }

    private AgentMessageResponseVO mapPythonResponse(PythonAgentChatResponseDTO source,
                                                     AgentSessionVO session,
                                                     LoginUser loginUser) {
        AgentMessageResponseVO target = new AgentMessageResponseVO();
        target.setSession(session);
        target.setNeedsUserSelection(source.isNeedsUserSelection());
        target.setAnswer(source.getError() == null
                ? safeText(source.getAnswer(), UNAVAILABLE_MESSAGE)
                : safeErrorMessage(source.getError().getCode()));

        List<AgentBusinessCardVO> cards = new ArrayList<>();
        List<AgentChoiceOptionVO> flattenedOptions = new ArrayList<>();
        for (PythonAgentChatResponseDTO.BusinessCard sourceCard : safeList(source.getCards())) {
            AgentBusinessCardVO card = new AgentBusinessCardVO();
            card.setCardType(safeText(sourceCard.getCardType(), "business_result"));
            card.setTitle(safeText(sourceCard.getTitle(), "查询结果"));
            card.setPrompt(safeText(sourceCard.getPrompt(), null));
            List<AgentChoiceOptionVO> options = new ArrayList<>();
            for (PythonAgentChatResponseDTO.UserOption sourceOption : safeList(sourceCard.getOptions())) {
                AgentChoiceOptionVO option = mapOption(sourceOption);
                options.add(option);
                flattenedOptions.add(option);
            }
            card.setOptions(options);
            card.setFields(safeCardFields(sourceCard.getFields()));
            cards.add(card);
        }
        target.setCards(cards);
        target.setOptions(flattenedOptions);
        target.setSuggestions(safeList(source.getSuggestions()).stream()
                .map(value -> safeText(value, null))
                .filter(value -> value != null && !value.isBlank())
                .limit(5)
                .toList());
        if (isAdmin(loginUser) && source.getDebug() != null) {
            target.setDebug(safeDebug(source.getDebug()));
        }
        return target;
    }

    private AgentChoiceOptionVO mapOption(PythonAgentChatResponseDTO.UserOption source) {
        AgentChoiceOptionVO option = new AgentChoiceOptionVO();
        option.setOptionId(safeText(source.getOptionId(), null));
        option.setOptionType(safeText(source.getOptionType(), "BUSINESS_OPTION"));
        option.setDisplayLabel(safeLabel(source.getDisplayLabel()));
        option.setDescription(safeText(source.getDescription(), null));
        option.setSupported(source.isSupported());
        option.setDisabledReason(safeText(source.getDisabledReason(), null));
        return option;
    }

    private AgentMessageResponseVO sanitizeLegacyResponse(AgentMessageResponseVO response, LoginUser loginUser) {
        if (response == null) {
            return unavailableResponse(null);
        }
        response.setAnswer(safeText(response.getAnswer(), UNAVAILABLE_MESSAGE));
        for (AgentChoiceOptionVO option : safeList(response.getOptions())) {
            option.setProductId(null);
            option.setWarehouseId(null);
            option.setDisplayLabel(safeLabel(option.getDisplayLabel()));
            option.setDescription(safeText(option.getDescription(), null));
            option.setDisabledReason(safeText(option.getDisabledReason(), null));
        }
        if (!isAdmin(loginUser)) {
            response.getToolCalls().clear();
            response.setDebug(null);
        }
        return response;
    }

    private boolean canFallback(String agentSessionId, AgentMessageRequestDTO request) {
        if (!properties.isFallbackEnabled()
                || pythonContextSessions.contains(agentSessionId)
                || selectedOption(request.getPageContext()) != null) {
            return false;
        }
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isBlank() || containsAny(message, "这些", "它", "刚才", "那个", "这个", "上述", "前面", "继续", "展开")) {
            return false;
        }
        return containsAny(message, "库存", "库位", "仓库", "容量", "托盘", "化验");
    }

    private AgentMessageResponseVO unavailableResponse(AgentSessionVO session) {
        AgentMessageResponseVO response = new AgentMessageResponseVO();
        response.setAnswer(UNAVAILABLE_MESSAGE);
        response.setSession(session);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> selectedOption(Map<String, Object> pageContext) {
        if (pageContext == null) {
            return null;
        }
        Object selected = pageContext.get("selectedOption");
        return selected instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private Map<String, Object> safePageContext(Map<String, Object> pageContext) {
        Map<String, Object> safe = new LinkedHashMap<>();
        if (pageContext == null) {
            return safe;
        }
        for (String key : SAFE_PAGE_CONTEXT_KEYS) {
            String value = safeScalar(pageContext.get(key));
            if (value != null) {
                safe.put(key, value);
            }
        }
        return safe;
    }

    private List<Map<String, String>> safeCardFields(List<Map<String, String>> fields) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> field : safeList(fields)) {
            Map<String, String> safe = new LinkedHashMap<>();
            field.forEach((key, value) -> {
                if (isSafeScalar(key) && isSafeScalar(value)) {
                    safe.put(key, value);
                }
            });
            if (!safe.isEmpty()) {
                result.add(safe);
            }
        }
        return result;
    }

    private Map<String, Object> safeDebug(JsonNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (isSafeScalar(entry.getKey()) && entry.getValue().isValueNode()) {
                    String value = entry.getValue().asText();
                    if (isSafeScalar(value)) {
                        result.put(entry.getKey(), value);
                    }
                }
            });
        }
        return result.isEmpty() ? null : result;
    }

    private void recordRuntimeAudit(String agentSessionId,
                                    Integer userId,
                                    String requestId,
                                    String traceId,
                                    AgentMessageRequestDTO request,
                                    String path,
                                    String resultCode,
                                    String errorCode,
                                    boolean fallbackUsed,
                                    AgentMessageResponseVO response,
                                    long durationMs) {
        try {
            AgentToolAuditDTO audit = new AgentToolAuditDTO();
            audit.setToolName("agent_runtime");
            audit.setToolCallId(requestId);
            audit.setUpstreamPath("python".equals(path) ? "/internal/agent/chat" : "legacy");
            audit.setArgumentsSummary("runtimeMode=" + properties.getMode().name().toLowerCase(Locale.ROOT)
                    + "; path=" + path
                    + "; requestId=" + requestId
                    + "; traceId=" + traceId
                    + "; eventType=" + (selectedOption(request.getPageContext()) == null
                    ? "user_message" : "candidate_selected")
                    + "; fallbackUsed=" + fallbackUsed);
            audit.setRequestSummary("messageLength=" + (request.getMessage() == null ? 0 : request.getMessage().length()));
            audit.setResponseSummary("needsUserSelection=" + response.isNeedsUserSelection()
                    + "; optionCount=" + response.getOptions().size());
            audit.setResultCode(resultCode);
            audit.setErrorCode(errorCode);
            audit.setDurationMs(durationMs);
            agentSessionService.recordToolAudit(agentSessionId, userId, audit);
        } catch (RuntimeException e) {
            log.warn("Agent runtime audit could not be recorded; errorCode=AGENT_RUNTIME_AUDIT_FAILED");
        }
    }

    private String safeErrorMessage(String code) {
        if (code == null) {
            return UNAVAILABLE_MESSAGE;
        }
        return switch (code) {
            case "UPSTREAM_UNAUTHORIZED" -> "当前登录会话无法完成该查询，请重新登录后重试。";
            case "UPSTREAM_PERMISSION_DENIED" -> "当前用户没有执行该只读查询的权限。";
            case "UPSTREAM_TIMEOUT" -> "查询仓储数据超时，请稍后重试。";
            default -> UNAVAILABLE_MESSAGE;
        };
    }

    private String safeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.matches("[A-Z0-9_]{1,80}") ? code : "PYTHON_AGENT_SERVICE_ERROR";
    }

    private String safeLabel(String value) {
        String safe = safeText(value, "候选项");
        return LABEL_ID.matcher(safe).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank() || INTERNAL_TEXT.matcher(value).find()) {
            return fallback;
        }
        String compact = value.replaceAll("(?m)^\\s*at\\s+.+$", "").trim();
        return compact.length() <= 2000 ? compact : compact.substring(0, 2000);
    }

    private boolean isSafeScalar(String value) {
        return value != null && !value.isBlank() && value.length() <= 500 && !INTERNAL_TEXT.matcher(value).find();
    }

    private String safeScalar(Object value) {
        if (!(value instanceof String text) || !isSafeScalar(text)) {
            return null;
        }
        return text;
    }

    private Object value(Map<String, Object> map, String key) {
        return map == null ? null : map.get(key);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private boolean isAdmin(LoginUser loginUser) {
        String roleCode = loginUser == null || loginUser.getUser() == null
                ? null : loginUser.getUser().getRoleCode();
        return "ADMIN".equals(roleCode) || "SUPER_ADMIN".equals(roleCode);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}