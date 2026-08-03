package com.Laibin.SugarInventory.agent.gateway;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.python.PythonAgentClient;
import com.Laibin.SugarInventory.agent.python.PythonAgentClientException;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatRequestDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentChatResponseDTO;
import com.Laibin.SugarInventory.agent.python.dto.PythonAgentStreamEventDTO;
import com.Laibin.SugarInventory.agent.runtime.AgentRuntimeProperties;
import com.Laibin.SugarInventory.agent.service.AgentInterruptStateService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentBusinessCardVO;
import com.Laibin.SugarInventory.agent.vo.AgentChoiceOptionVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Primary
@Service
public class RuntimeRoutingAgentGatewayService implements AgentGatewayService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeRoutingAgentGatewayService.class);
    private static final String UNAVAILABLE_MESSAGE = "Agent 服务暂不可用，本次未执行任何业务查询或变更。";
    private static final Set<String> SAFE_PAGE_CONTEXT_KEYS = Set.of("path", "routeName", "pageTitle");
    private static final Set<String> STREAM_EVENT_TYPES = Set.of(
            "message_start", "progress", "clarification", "text_delta", "card", "error", "message_end",
            "tool_start", "tool_end", "debug", "audit", "heartbeat", "cancelled", "timeout", "fallback");
    private static final Set<String> DEBUG_STREAM_EVENT_TYPES = Set.of("tool_start", "tool_end", "debug");
    private static final Set<String> INTERNAL_STREAM_EVENT_TYPES = Set.of("audit");
    private static final String KNOWLEDGE_CARD_TYPE = "knowledge_evidence";
    private static final Set<String> KNOWLEDGE_FIELD_LABELS = Set.of("内容", "来源");
    private static final Set<String> KNOWLEDGE_GOAL_TYPES = Set.of(
            "PROCESS_KNOWLEDGE_QUERY", "ENTERPRISE_KNOWLEDGE_QUERY");
    private static final Set<String> KNOWLEDGE_STATUSES = Set.of(
            "SUCCEEDED", "DEGRADED", "NO_DATA", "UNAVAILABLE", "FORBIDDEN", "INVALID_QUERY", "ERROR");
    private static final Set<String> KNOWLEDGE_DOMAINS = Set.of(
            "PROCESS", "COMPANY", "PRODUCT_MARKETING", "CERTIFICATION", "SALES");
    private static final Pattern INTERNAL_TEXT = Pattern.compile(
            "(?i)(authorization|bearer\\s+|delegationToken|refreshToken|productId|warehouseId|toolName|stackTrace|jdbc:|\\bSUCCESS\\b|\\btoken\\b)");
    private static final Pattern UNSAFE_KNOWLEDGE_TEXT = Pattern.compile(
            "(?i)([a-z]:[\\\\/]|\\\\\\\\[^\\s]+[\\\\/]|file://|(?:^|\\s)/(?:[a-z0-9._-]+/)+|"
                    + "\\b(?:document|chunk|evidence|embedding|score|prompt)(?:[_-]?id)?\\s*[:=]|"
                    + "\\b(?:doc|chunk|ev)_[a-z0-9_-]+\\b)");
    private static final Pattern LABEL_ID = Pattern.compile("\\s*[(（]#\\d+[)）]\\s*");

    private final AgentSessionService agentSessionService;
    private final AgentGatewayService legacyGateway;
    private final PythonAgentClient pythonAgentClient;
    private final AgentRuntimeProperties properties;
    private final AgentInterruptStateService interruptStateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> pythonContextSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> legacyFallbackSessions = ConcurrentHashMap.newKeySet();
    private final Set<String> cancelledMessageKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, ActiveStream> activeStreams = new ConcurrentHashMap<>();

    public RuntimeRoutingAgentGatewayService(
            AgentSessionService agentSessionService,
            @Qualifier("legacyAgentGatewayService") AgentGatewayService legacyGateway,
            PythonAgentClient pythonAgentClient,
            AgentRuntimeProperties properties,
            AgentInterruptStateService interruptStateService) {
        this.agentSessionService = agentSessionService;
        this.legacyGateway = legacyGateway;
        this.pythonAgentClient = pythonAgentClient;
        this.properties = properties;
        this.interruptStateService = interruptStateService;
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
            recordRuntimeAudit(agentSessionId, userId, null, requestId, traceId, request,
                    "legacy", "SUCCESS", null, legacyFallbackSessions.contains(agentSessionId),
                    legacyResponse, elapsedMillis(legacyStartedAt));
            return legacyResponse;
        }

        AgentSession session = agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentSessionVO sessionVO = agentSessionService.toSessionVO(session, loginUser);
        String requestId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long startedAt = System.nanoTime();
        boolean fallbackUsed = false;
        String resultCode = "SUCCESS";
        String errorCode = null;
        String path = "python";
        AgentMessageResponseVO result;
        JsonNode pythonReviewTrace = null;

        String invalidResumeStatus = invalidResumeStatus(sessionVO, request);
        if (invalidResumeStatus != null) {
            result = invalidResumeResponse(sessionVO, invalidResumeStatus);
            recordRuntimeAudit(agentSessionId, sessionVO.getUserId(), messageId, requestId, traceId, request,
                    "python", "INVALID", "HITL_INTERRUPT_NOT_PENDING", false, result, elapsedMillis(startedAt));
            return result;
        }

        try {
            if (!pythonAgentClient.isHealthy()) {
                throw new PythonAgentClientException("PYTHON_AGENT_UNAVAILABLE", true);
            }
            PythonAgentChatRequestDTO pythonRequest = buildPythonRequest(
                    loginUser, sessionVO, request, requestId, traceId);
            pythonRequest.setMessageId(messageId);
            PythonAgentChatResponseDTO pythonResponse = pythonAgentClient.chat(pythonRequest);
            pythonContextSessions.add(agentSessionId);
            pythonReviewTrace = pythonResponse.getReviewTrace();
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

        long durationMs = elapsedMillis(startedAt);
        recordRuntimeAudit(agentSessionId, sessionVO.getUserId(), messageId, requestId, traceId, request,
                path, resultCode, errorCode, fallbackUsed, result, durationMs);
        if ("python".equals(path) && pythonReviewTrace != null) {
            recordPythonTraceAudits(
                    sessionVO, messageId, pythonReviewTrace, "/internal/agent/chat", resultCode, errorCode, durationMs);
        }
        return result;
    }

    @Override
    public SseEmitter streamMessage(LoginUser loginUser, String agentSessionId, AgentMessageRequestDTO request) {
        SseEmitter emitter = new SseEmitter((long) Math.max(1000, properties.getPythonTimeoutMs()) + 5000);
        if (properties.getMode() == AgentRuntimeProperties.Mode.LEGACY
                || legacyFallbackSessions.contains(agentSessionId)) {
            CompletableFuture.runAsync(() -> streamLegacy(loginUser, agentSessionId, request, emitter, false));
            return emitter;
        }

        AgentSession session = agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentSessionVO sessionVO = agentSessionService.toSessionVO(session, loginUser);
        String requestId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String invalidResumeStatus = invalidResumeStatus(sessionVO, request);
        if (invalidResumeStatus != null) {
            CompletableFuture.runAsync(() -> streamInvalidResume(
                    sessionVO, request, emitter, requestId, traceId, messageId, invalidResumeStatus));
            return emitter;
        }
        PythonAgentChatRequestDTO pythonRequest = buildPythonRequest(loginUser, sessionVO, request, requestId, traceId);
        pythonRequest.setMessageId(messageId);
        recordResumeRequestedIfNeeded(sessionVO, request);
        ActiveStream activeStream = new ActiveStream(agentSessionId, messageId, emitter);
        activeStreams.put(streamKey(agentSessionId, messageId), activeStream);
        emitter.onTimeout(() -> timeoutActiveStream(activeStream));
        CompletableFuture.runAsync(() -> streamPython(
                loginUser, sessionVO, request, activeStream, pythonRequest, requestId, traceId));
        return emitter;
    }

    private void timeoutActiveStream(ActiveStream activeStream) {
        if (activeStream.isClosed()) {
            return;
        }
        activeStream.timeout();
        activeStream.close();
        CompletableFuture.runAsync(() -> {
            try {
                pythonAgentClient.cancel(activeStream.agentSessionId(), activeStream.messageId());
            } catch (PythonAgentClientException e) {
                log.warn("Python stream timeout cancellation failed; errorCode={}", safeCode(e.getCode()));
            }
        });
    }

    @Override
    public boolean cancelMessage(LoginUser loginUser, String agentSessionId, String messageId) {
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        if (messageId == null || !messageId.matches("msg_[a-zA-Z0-9]{1,56}")) {
            return false;
        }
        String key = streamKey(agentSessionId, messageId);
        cancelledMessageKeys.add(key);
        ActiveStream activeStream = activeStreams.get(key);
        if (activeStream != null) {
            activeStream.cancel();
            try {
                synchronized (activeStream) {
                    if (!activeStream.isClosed()) {
                        sendEnvelope(activeStream.emitter(), "cancelled", agentSessionId, messageId,
                                activeStream.nextSequence(), Map.of("message", "已取消本次生成。"));
                        sendEnvelope(activeStream.emitter(), "message_end", agentSessionId, messageId,
                                activeStream.nextSequence(), Map.of("finishReason", "cancelled"));
                        activeStream.close();
                    }
                }
            } catch (StreamClientDisconnectedException ignored) {
                activeStream.close();
            }
        }
        try {
            pythonAgentClient.cancel(agentSessionId, messageId);
        } catch (PythonAgentClientException e) {
            log.warn("Python cancellation notification failed; errorCode={}", safeCode(e.getCode()));
        }
        return true;
    }

    private void streamPython(LoginUser loginUser,
                              AgentSessionVO session,
                              AgentMessageRequestDTO request,
                              ActiveStream activeStream,
                              PythonAgentChatRequestDTO pythonRequest,
                              String requestId,
                              String traceId) {
        long startedAt = System.nanoTime();
        String resultCode = "COMPLETED";
        String errorCode = null;
        boolean fallbackUsed = false;
        AgentMessageResponseVO auditResponse = unavailableResponse(session);
        try {
            if (!pythonAgentClient.isHealthy()) {
                throw new PythonAgentClientException("PYTHON_AGENT_UNAVAILABLE", true);
            }
            pythonAgentClient.stream(pythonRequest, event -> {
                pythonContextSessions.add(session.getAgentSessionId());
                sendPythonStreamEvent(activeStream, event, session, loginUser);
            });
            resultCode = activeStream.resultCode();
            auditResponse.setAnswer("stream completed");
            activeStream.close();
        } catch (StreamClientDisconnectedException e) {
            resultCode = activeStream.isCancelled() ? "CLIENT_CANCELLED" : "CLIENT_DISCONNECTED";
            errorCode = "CLIENT_DISCONNECTED";
            activeStream.close();
        } catch (InvalidStreamEventException e) {
            resultCode = "SECURITY_FILTERED";
            errorCode = "INVALID_STREAM_EVENT";
            emitStreamFailure(activeStream.emitter(), session.getAgentSessionId(), errorCode, UNAVAILABLE_MESSAGE);
        } catch (PythonAgentClientException e) {
            errorCode = safeCode(e.getCode());
            if (canFallback(session.getAgentSessionId(), request)) {
                fallbackUsed = true;
                legacyFallbackSessions.add(session.getAgentSessionId());
                streamLegacy(loginUser, session.getAgentSessionId(), request, activeStream.emitter(), true);
                resultCode = "COMPLETED";
                return;
            }
            resultCode = "PYTHON_AGENT_TIMEOUT".equals(e.getCode()) ? "PYTHON_TIMEOUT"
                    : pythonContextSessions.contains(session.getAgentSessionId()) ? "FALLBACK_BLOCKED" : "PYTHON_ERROR";
            emitStreamFailure(activeStream.emitter(), session.getAgentSessionId(), e.getCode(), safeErrorMessage(e.getCode()));
        } catch (RuntimeException e) {
            resultCode = activeStream.isCancelled() ? "CLIENT_CANCELLED" : "PYTHON_ERROR";
            errorCode = "PYTHON_AGENT_SERVICE_ERROR";
            if (!activeStream.isCancelled()) {
                emitStreamFailure(activeStream.emitter(), session.getAgentSessionId(), errorCode, UNAVAILABLE_MESSAGE);
            }
        } finally {
            if (activeStream.isCancelled() && "CLIENT_CANCELLED".equals(activeStream.resultCode())) {
                resultCode = "CLIENT_CANCELLED";
                errorCode = "CLIENT_CANCELLED";
            }
            recordRuntimeAudit(session.getAgentSessionId(), session.getUserId(), activeStream.messageId(), requestId, traceId, request,
                    "python_stream", resultCode, errorCode, fallbackUsed, auditResponse, elapsedMillis(startedAt));
            recordAgentHandoffAudit(session, activeStream, resultCode, errorCode, elapsedMillis(startedAt));
            recordKnowledgeSearchAudit(
                    session,
                    activeStream.messageId(),
                    activeStream.knowledgeAuditSnapshot(),
                    "/internal/agent/chat/stream",
                    errorCode,
                    elapsedMillis(startedAt));
            activeStreams.remove(streamKey(session.getAgentSessionId(), activeStream.messageId()), activeStream);
            cancelledMessageKeys.remove(streamKey(session.getAgentSessionId(), activeStream.messageId()));
        }
    }

    private void streamLegacy(LoginUser loginUser,
                              String agentSessionId,
                              AgentMessageRequestDTO request,
                              SseEmitter emitter,
                              boolean fallbackEvent) {
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            int sequence = 1;
            if (fallbackEvent) {
                sendEnvelope(emitter, "fallback", agentSessionId, messageId, sequence++,
                        Map.of("message", "Python Agent 暂不可用，已切换到基础只读查询。"));
            }
            AgentMessageResponseVO response = sanitizeLegacyResponse(
                    legacyGateway.handleMessage(loginUser, agentSessionId, request), loginUser);
            sendEnvelope(emitter, "message_start", agentSessionId, messageId, sequence++, Map.of("role", "assistant"));
            if (response.isNeedsUserSelection() && !safeList(response.getOptions()).isEmpty()) {
                List<Map<String, Object>> options = safeList(response.getOptions()).stream()
                        .map(option -> {
                            Map<String, Object> item = new LinkedHashMap<String, Object>();
                            item.put("optionType", safeText(option.getOptionType(), "BUSINESS_OPTION"));
                            item.put("displayLabel", safeLabel(option.getDisplayLabel()));
                            item.put("description", safeText(option.getDescription(), null));
                            item.put("supported", !Boolean.FALSE.equals(option.getSupported()));
                            item.put("disabledReason", safeText(option.getDisabledReason(), null));
                            item.values().removeIf(java.util.Objects::isNull);
                            return item;
                        })
                        .toList();
                sendEnvelope(emitter, "clarification", agentSessionId, messageId, sequence++, Map.of(
                        "prompt", safeText(response.getAnswer(), UNAVAILABLE_MESSAGE),
                        "interruptKind", "LEGACY_CLARIFICATION",
                        "options", options));
                sendEnvelope(emitter, "message_end", agentSessionId, messageId, sequence,
                        Map.of("finishReason", "clarification_required", "interruptKind", "LEGACY_CLARIFICATION"));
            } else {
                sendEnvelope(emitter, "text_delta", agentSessionId, messageId, sequence++,
                        Map.of("text", safeText(response.getAnswer(), UNAVAILABLE_MESSAGE)));
                sendEnvelope(emitter, "message_end", agentSessionId, messageId, sequence,
                        Map.of("finishReason", "completed"));
            }
            emitter.complete();
        } catch (RuntimeException e) {
            emitStreamFailure(emitter, agentSessionId, "LEGACY_AGENT_ERROR", UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public void clearSession(String agentSessionId) {
        if (properties.getMode() == AgentRuntimeProperties.Mode.PYTHON
                || pythonContextSessions.contains(agentSessionId)) {
            try {
                pythonAgentClient.clearSession(agentSessionId);
            } catch (PythonAgentClientException e) {
                log.warn("Python Agent session cleanup failed; errorCode={}", safeCode(e.getCode()));
            }
        }
        pythonContextSessions.remove(agentSessionId);
        legacyFallbackSessions.remove(agentSessionId);
        activeStreams.entrySet().removeIf(entry -> entry.getValue().agentSessionId().equals(agentSessionId));
        cancelledMessageKeys.removeIf(key -> key.startsWith(agentSessionId + ":"));
        interruptStateService.cancelSessionInterrupts(agentSessionId);
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
            message.setInterruptId(safeScalar(selectedOption.get("interruptId")));
            message.setResumeToken(safeScalar(selectedOption.get("resumeToken")));
            message.setAction(firstNonBlank(safeScalar(selectedOption.get("action")), "SELECT_OPTION"));
            message.setClientRequestId(safeScalar(selectedOption.get("clientRequestId")));
            PythonAgentChatRequestDTO.Selection selection = new PythonAgentChatRequestDTO.Selection();
            selection.setOptionId(safeScalar(selectedOption.get("optionId")));
            selection.setOptionType(safeScalar(selectedOption.get("optionType")));
            selection.setDisplayLabel(safeLabel(safeScalar(selectedOption.get("displayLabel"))));
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
        target.setAnswer(source.getError() == null
                ? safeText(source.getAnswer(), UNAVAILABLE_MESSAGE)
                : safeErrorMessage(source.getError().getCode()));

        List<AgentBusinessCardVO> cards = new ArrayList<>();
        List<AgentChoiceOptionVO> flattenedOptions = new ArrayList<>();
        for (PythonAgentChatResponseDTO.BusinessCard sourceCard : safeList(source.getCards())) {
            if (KNOWLEDGE_CARD_TYPE.equals(sourceCard.getCardType())) {
                cards.add(mapKnowledgeCard(sourceCard));
                continue;
            }
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
        target.setNeedsUserSelection(source.isNeedsUserSelection() && !flattenedOptions.isEmpty());
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

    private AgentBusinessCardVO mapKnowledgeCard(PythonAgentChatResponseDTO.BusinessCard source) {
        AgentBusinessCardVO card = new AgentBusinessCardVO();
        card.setCardType(KNOWLEDGE_CARD_TYPE);
        card.setTitle(safeKnowledgeText(source.getTitle(), "现行资料", 500));
        card.setPrompt(null);
        card.setOptions(List.of());
        card.setFields(safeKnowledgeFields(source.getFields()));
        return card;
    }

    private List<Map<String, Object>> safeKnowledgeFields(List<Map<String, Object>> fields) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seenLabels = new java.util.LinkedHashSet<>();
        for (Map<String, Object> field : safeList(fields)) {
            if (result.size() >= KNOWLEDGE_FIELD_LABELS.size()) {
                break;
            }
            String label = field.get("label") instanceof String text ? text : null;
            if (!KNOWLEDGE_FIELD_LABELS.contains(label) || !seenLabels.add(label)) {
                continue;
            }
            String rawValue = field.get("value") instanceof String text ? text : null;
            String value = safeKnowledgeText(rawValue, null, "内容".equals(label) ? 800 : 500);
            if (value != null) {
                result.add(Map.of("label", label, "value", value));
            }
        }
        return result;
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
        return false;
    }

    private AgentMessageResponseVO unavailableResponse(AgentSessionVO session) {
        AgentMessageResponseVO response = new AgentMessageResponseVO();
        response.setAnswer(UNAVAILABLE_MESSAGE);
        response.setSession(session);
        return response;
    }

    private String invalidResumeStatus(AgentSessionVO session, AgentMessageRequestDTO request) {
        Map<String, Object> selectedOption = selectedOption(request.getPageContext());
        if (selectedOption == null) {
            return null;
        }
        String interruptId = safeScalar(selectedOption.get("interruptId"));
        if (interruptId == null) {
            return "MISSING";
        }
        String status = interruptStateService.findOwnedStatus(session.getAgentSessionId(), session.getUserId(), interruptId);
        return "PENDING".equals(status) ? null : firstNonBlank(status, "MISSING");
    }

    private AgentMessageResponseVO invalidResumeResponse(AgentSessionVO session, String status) {
        AgentMessageResponseVO response = new AgentMessageResponseVO();
        response.setSession(session);
        response.setAnswer(switch (status) {
            case "RESUMED" -> "这个选择已经处理过了。";
            case "EXPIRED" -> "这个确认已过期，请重新发起查询。";
            case "CANCELLED" -> "该选择已失效，请重新发起查询。";
            case "REJECTED" -> "这个确认已被拒绝。";
            default -> "当前没有等待选择的业务候选项，请重新描述要查询的内容。";
        });
        return response;
    }

    private void streamInvalidResume(AgentSessionVO session,
                                     AgentMessageRequestDTO request,
                                     SseEmitter emitter,
                                     String requestId,
                                     String traceId,
                                     String messageId,
                                     String status) {
        long startedAt = System.nanoTime();
        AgentMessageResponseVO response = invalidResumeResponse(session, status);
        try {
            sendEnvelope(emitter, "message_start", session.getAgentSessionId(), messageId, 1,
                    Map.of("role", "assistant"));
            sendEnvelope(emitter, "text_delta", session.getAgentSessionId(), messageId, 2,
                    Map.of("text", response.getAnswer()));
            sendEnvelope(emitter, "message_end", session.getAgentSessionId(), messageId, 3,
                    Map.of("finishReason", "completed"));
            emitter.complete();
        } catch (RuntimeException e) {
            emitter.completeWithError(e);
        } finally {
            recordRuntimeAudit(session.getAgentSessionId(), session.getUserId(), messageId, requestId, traceId, request,
                    "python_stream", "INVALID", "HITL_INTERRUPT_NOT_PENDING", false,
                    response, elapsedMillis(startedAt));
        }
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

    private List<Map<String, Object>> safeCardFields(List<Map<String, Object>> fields) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> field : safeList(fields).stream().limit(100).toList()) {
            Map<String, Object> safe = new LinkedHashMap<>();
            field.forEach((key, value) -> {
                if (isSafeEventKey(key)) {
                    Object safeValue = safeObjectValue(value);
                    if (safeValue != null) {
                        safe.put(key, safeValue);
                    }
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

    private void sendPythonStreamEvent(ActiveStream activeStream,
                                       PythonAgentStreamEventDTO source,
                                       AgentSessionVO session,
                                       LoginUser loginUser) {
        if (activeStream.isCancelled()
                || cancelledMessageKeys.contains(streamKey(session.getAgentSessionId(), activeStream.messageId()))) {
            return;
        }
        String type = source.getType();
        if (!STREAM_EVENT_TYPES.contains(type)) {
            throw new InvalidStreamEventException();
        }
        if (!activeStream.messageId().equals(source.getMessageId())
                || (!INTERNAL_STREAM_EVENT_TYPES.contains(type) && containsUnsafeValue(source.getPayload()))) {
            throw new InvalidStreamEventException();
        }
        validateInterruptStreamEvent(activeStream, source);
        if (INTERNAL_STREAM_EVENT_TYPES.contains(type)) {
            activeStream.recordAgentHandoff(source.getPayload());
            return;
        }
        if (DEBUG_STREAM_EVENT_TYPES.contains(type) && !isAdmin(loginUser)) {
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", safeText(source.getEventId(), "evt_unknown"));
        event.put("messageId", safeText(source.getMessageId(), "msg_unknown"));
        event.put("agentSessionId", session.getAgentSessionId());
        event.put("type", type);
        event.put("sequence", source.getSequence());
        event.put("payload", safeStreamPayload(type, source.getPayload()));
        recordInterruptEvent(activeStream, source, session);
        activeStream.observe(source);
        activeStream.updateSequence(source.getSequence());
        sendSse(activeStream.emitter(), type, event);
    }

    private void validateInterruptStreamEvent(ActiveStream activeStream, PythonAgentStreamEventDTO source) {
        JsonNode payload = source.getPayload();
        if (payload == null || payload.isNull()) {
            return;
        }
        if ("clarification".equals(source.getType()) || "hitl_interrupt".equals(source.getType())) {
            String interruptId = safeScalar(payload.path("interruptId").asText(null));
            if (interruptId == null) {
                throw new InvalidStreamEventException();
            }
            activeStream.recordInterrupt(interruptId);
            return;
        }
        if ("message_end".equals(source.getType())
                && "interrupt_required".equals(payload.path("finishReason").asText(null))) {
            String interruptId = safeScalar(payload.path("interruptId").asText(null));
            if (interruptId == null || !activeStream.hasInterrupt(interruptId)) {
                throw new InvalidStreamEventException();
            }
        }
    }

    private void recordResumeRequestedIfNeeded(AgentSessionVO session, AgentMessageRequestDTO request) {
        Map<String, Object> selectedOption = selectedOption(request.getPageContext());
        if (selectedOption == null) {
            return;
        }
        interruptStateService.recordResumeRequested(
                session.getAgentSessionId(),
                session.getUserId(),
                safeScalar(selectedOption.get("interruptId")),
                safeScalar(selectedOption.get("action")),
                safeScalar(selectedOption.get("optionId")),
                safeScalar(selectedOption.get("previewId")),
                safeScalar(selectedOption.get("clientRequestId")));
    }

    private void recordInterruptEvent(ActiveStream activeStream,
                                      PythonAgentStreamEventDTO source,
                                      AgentSessionVO session) {
        JsonNode payload = source.getPayload();
        if (payload == null || payload.isNull()) {
            return;
        }
        if ("clarification".equals(source.getType()) || "hitl_interrupt".equals(source.getType())) {
            interruptStateService.recordCreated(
                    session.getAgentSessionId(),
                    session.getUserId(),
                    activeStream.messageId(),
                    safeScalar(payload.path("interruptId").asText(null)),
                    safeScalar(firstNonBlank(payload.path("interruptKind").asText(null), payload.path("kind").asText(null))),
                    parseDateTime(payload.path("expiresAt").asText(null)));
            return;
        }
        if (!"message_end".equals(source.getType())) {
            return;
        }
        String interruptId = safeScalar(payload.path("interruptId").asText(null));
        if (interruptId == null) {
            return;
        }
        String status = interruptStatusForFinishReason(safeScalar(payload.path("finishReason").asText(null)));
        if (status == null) {
            return;
        }
        interruptStateService.recordTerminal(
                session.getAgentSessionId(),
                session.getUserId(),
                interruptId,
                status,
                activeStream.resultCode(),
                null);
    }

    private String interruptStatusForFinishReason(String finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case "completed" -> "RESUMED";
            case "cancelled" -> "CANCELLED";
            case "rejected" -> "REJECTED";
            case "expired" -> "EXPIRED";
            case "error" -> "ERROR";
            case "timeout" -> "TIMEOUT";
            default -> null;
        };
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (RuntimeException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    private void emitStreamFailure(SseEmitter emitter, String agentSessionId, String code, String message) {
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String finishReason = "PYTHON_AGENT_TIMEOUT".equals(code) || "UPSTREAM_TIMEOUT".equals(code) ? "timeout" : "error";
        try {
            sendEnvelope(emitter, "error", agentSessionId, messageId, 1,
                    Map.of("message", safeText(message, UNAVAILABLE_MESSAGE), "retryable", true));
            sendEnvelope(emitter, "message_end", agentSessionId, messageId, 2,
                    Map.of("finishReason", finishReason));
            emitter.complete();
        } catch (RuntimeException e) {
            emitter.completeWithError(e);
        }
    }

    private void sendEnvelope(SseEmitter emitter,
                              String type,
                              String agentSessionId,
                              String messageId,
                              int sequence,
                              Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt_" + String.format("%06d", sequence));
        event.put("messageId", messageId);
        event.put("agentSessionId", agentSessionId);
        event.put("type", type);
        event.put("sequence", sequence);
        event.put("payload", safeObjectValue(payload));
        sendSse(emitter, type, event);
    }

    private void sendSse(SseEmitter emitter, String type, Map<String, Object> event) {
        try {
            emitter.send(SseEmitter.event().name(type).data(event));
        } catch (IOException | IllegalStateException e) {
            throw new StreamClientDisconnectedException();
        }
    }

    private Object safeJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return safeObjectValue(objectMapper.convertValue(node, Object.class));
    }

    private Object safeStreamPayload(String type, JsonNode node) {
        if ("card".equals(type)
                && node != null
                && node.isObject()
                && KNOWLEDGE_CARD_TYPE.equals(node.path("cardType").asText(null))) {
            return safeKnowledgeCardPayload(node);
        }
        if (!"text_delta".equals(type)) {
            return safeJsonValue(node);
        }
        if (node == null || !node.isObject() || !node.path("text").isTextual()) {
            return Map.of();
        }
        String text = safeStreamText(node.path("text").asText());
        return text == null ? Map.of() : Map.of("text", text);
    }

    private Map<String, Object> safeKnowledgeCardPayload(JsonNode node) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", KNOWLEDGE_CARD_TYPE);
        card.put("title", safeKnowledgeText(node.path("title").asText(null), "现行资料", 500));
        List<Map<String, Object>> fields = new ArrayList<>();
        Set<String> seenLabels = new java.util.LinkedHashSet<>();
        JsonNode sourceFields = node.path("fields");
        if (sourceFields.isArray()) {
            for (JsonNode field : sourceFields) {
                if (fields.size() >= KNOWLEDGE_FIELD_LABELS.size()) {
                    break;
                }
                String label = field.path("label").asText(null);
                if (!KNOWLEDGE_FIELD_LABELS.contains(label) || !seenLabels.add(label)) {
                    continue;
                }
                String value = safeKnowledgeText(
                        field.path("value").asText(null), null, "内容".equals(label) ? 800 : 500);
                if (value != null) {
                    fields.add(Map.of("label", label, "value", value));
                }
            }
        }
        card.put("fields", fields);
        return card;
    }

    private String safeStreamText(String value) {
        if (value == null || value.isEmpty() || value.length() > 2000 || INTERNAL_TEXT.matcher(value).find()) {
            return null;
        }
        String safe = value.replaceAll("(?m)^\\s*at\\s+.+$", "");
        return safe.isEmpty() ? null : safe;
    }

    private Object safeObjectValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((rawKey, rawValue) -> {
                String key = rawKey == null ? null : String.valueOf(rawKey);
                if (isSafeEventKey(key)) {
                    Object safeValue = safeObjectValue(rawValue);
                    if (safeValue != null) {
                        safe.put(key, safeValue);
                    }
                }
            });
            return safe;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(this::safeObjectValue)
                    .filter(item -> item != null)
                    .toList();
        }
        if (value instanceof String text) {
            return safeText(text, null);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return null;
    }

    private boolean isSafeEventKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.replace("_", "").toLowerCase(Locale.ROOT);
        return !Set.of(
                "authorization",
                "delegationtoken",
                "password",
                "productid",
                "refreshtoken",
                "stacktrace",
                "token",
                "toolname",
                "warehouseid"
        ).contains(normalized) && isSafeScalar(key);
    }

    private boolean containsUnsafeValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!isSafeEventKey(entry.getKey()) || containsUnsafeValue(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (containsUnsafeValue(item)) {
                    return true;
                }
            }
            return false;
        }
        return node.isTextual()
                && (node.asText().length() > 2000 || INTERNAL_TEXT.matcher(node.asText()).find());
    }

    private String streamKey(String agentSessionId, String messageId) {
        return agentSessionId + ":" + messageId;
    }

    private void recordRuntimeAudit(String agentSessionId,
                                    Integer userId,
                                    String messageId,
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
            Map<String, Object> selectedOption = selectedOption(request.getPageContext());
            AgentToolAuditDTO audit = new AgentToolAuditDTO();
            audit.setToolName("agent_runtime");
            audit.setToolCallId(requestId);
            audit.setMessageId(messageId);
            audit.setUpstreamPath(switch (path) {
                case "python" -> "/internal/agent/chat";
                case "python_stream" -> "/internal/agent/chat/stream";
                default -> "legacy";
            });
            audit.setArgumentsSummary("runtimeMode=" + properties.getMode().name().toLowerCase(Locale.ROOT)
                    + "; path=" + path
                    + "; requestId=" + requestId
                    + "; traceId=" + traceId
                    + "; eventType=" + (selectedOption == null ? "user_message" : "candidate_selected")
                    + "; interruptId=" + (selectedOption == null ? null : safeScalar(selectedOption.get("interruptId")))
                    + "; resumeAction=" + (selectedOption == null ? null : safeScalar(selectedOption.get("action")))
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

    private void recordAgentHandoffAudit(AgentSessionVO session,
                                         ActiveStream activeStream,
                                         String resultCode,
                                         String errorCode,
                                         long durationMs) {
        String summary = activeStream.agentHandoffSummary();
        if (summary == null) {
            return;
        }
        recordAgentHandoffAudit(
                session,
                activeStream.messageId(),
                summary,
                "/internal/agent/chat/stream",
                resultCode,
                errorCode,
                durationMs);
    }

    private void recordPythonTraceAudits(AgentSessionVO session,
                                         String messageId,
                                         JsonNode trace,
                                         String upstreamPath,
                                         String resultCode,
                                         String errorCode,
                                         long durationMs) {
        String handoffSummary = handoffSummaryFromTrace(trace);
        if (handoffSummary != null) {
            recordAgentHandoffAudit(
                    session, messageId, handoffSummary, upstreamPath, resultCode, errorCode, durationMs);
        }
        recordKnowledgeSearchAudit(
                session, messageId, knowledgeAuditFromTrace(trace), upstreamPath, errorCode, durationMs);
    }

    private void recordAgentHandoffAudit(AgentSessionVO session,
                                         String messageId,
                                         String summary,
                                         String upstreamPath,
                                         String resultCode,
                                         String errorCode,
                                         long durationMs) {
        try {
            AgentToolAuditDTO audit = new AgentToolAuditDTO();
            audit.setToolName("agent_handoff");
            audit.setToolCallId(messageId);
            audit.setMessageId(messageId);
            audit.setUpstreamPath(upstreamPath);
            audit.setArgumentsSummary(summary);
            audit.setRequestSummary("sourceAgent=main_agent");
            audit.setResponseSummary("resultCode=" + resultCode);
            audit.setResultCode(resultCode);
            audit.setErrorCode(errorCode);
            audit.setDurationMs(durationMs);
            agentSessionService.recordToolAudit(session.getAgentSessionId(), session.getUserId(), audit);
        } catch (RuntimeException e) {
            log.warn("Agent handoff audit could not be recorded; errorCode=AGENT_HANDOFF_AUDIT_FAILED");
        }
    }

    private void recordKnowledgeSearchAudit(AgentSessionVO session,
                                            String messageId,
                                            KnowledgeAuditSnapshot knowledge,
                                            String upstreamPath,
                                            String fallbackErrorCode,
                                            long durationMs) {
        if (knowledge == null) {
            return;
        }
        boolean successful = Set.of("SUCCEEDED", "DEGRADED", "NO_DATA").contains(knowledge.status());
        String errorCode = successful ? null : firstNonBlank(fallbackErrorCode, switch (knowledge.status()) {
            case "UNAVAILABLE" -> "RAG_UNAVAILABLE";
            case "FORBIDDEN" -> "UPSTREAM_PERMISSION_DENIED";
            case "INVALID_QUERY" -> "UPSTREAM_BAD_REQUEST";
            default -> "RAG_SEARCH_FAILED";
        });
        try {
            AgentToolAuditDTO audit = new AgentToolAuditDTO();
            audit.setToolName("knowledge_search");
            audit.setToolCallId(messageId);
            audit.setMessageId(messageId);
            audit.setUpstreamPath(upstreamPath);
            audit.setArgumentsSummary("targetAgent=knowledge_expert"
                    + "; goalType=" + knowledge.goalType()
                    + "; knowledgeDomains=" + knowledge.knowledgeDomains());
            audit.setRequestSummary("source=approved_corpus");
            audit.setResponseSummary("status=" + knowledge.status()
                    + "; corpusVersion=" + firstNonBlank(knowledge.corpusVersion(), "unknown")
                    + "; evidenceCount=" + knowledge.evidenceCount()
                    + "; degraded=" + knowledge.degraded());
            audit.setResultCode(successful ? "SUCCESS" : "ERROR");
            audit.setErrorCode(safeCode(errorCode));
            audit.setDurationMs(durationMs);
            agentSessionService.recordToolAudit(session.getAgentSessionId(), session.getUserId(), audit);
        } catch (RuntimeException e) {
            log.warn("Knowledge search audit could not be recorded; errorCode=KNOWLEDGE_SEARCH_AUDIT_FAILED");
        }
    }

    private String handoffSummaryFromTrace(JsonNode trace) {
        if (trace == null || trace.isNull()) {
            return null;
        }
        JsonNode handoff = trace.path("agent_handoff");
        String targetAgent = safeAuditIdentifier(handoff.path("target_agent").asText(null));
        String businessDomain = safeAuditIdentifier(handoff.path("business_domain").asText(null));
        String mode = safeAuditIdentifier(handoff.path("mode").asText(null));

        if (targetAgent == null) {
            targetAgent = safeAuditIdentifier(trace.path("expertLoop").path("expertAgent").asText(null));
            if (targetAgent != null) {
                mode = "llm_delegate";
            }
        }
        KnowledgeAuditSnapshot knowledge = knowledgeAuditFromTrace(trace);
        if (targetAgent == null && knowledge != null) {
            targetAgent = "knowledge_expert";
            mode = "delegate";
        }
        if (targetAgent == null) {
            return null;
        }
        if (businessDomain == null && "knowledge_expert".equals(targetAgent)) {
            businessDomain = "knowledge";
        }
        return "targetAgent=" + targetAgent
                + "; businessDomain=" + firstNonBlank(businessDomain, "unknown")
                + "; handoffMode=" + firstNonBlank(mode, "unknown");
    }

    private KnowledgeAuditSnapshot knowledgeAuditFromTrace(JsonNode trace) {
        if (trace == null || trace.isNull()) {
            return null;
        }
        JsonNode node = trace.path("knowledgeAudit");
        if (!node.isObject()
                || !"knowledge_expert".equals(node.path("targetAgent").asText(null))) {
            return null;
        }
        String goalType = node.path("goalType").asText(null);
        String status = node.path("status").asText(null);
        if (!KNOWLEDGE_GOAL_TYPES.contains(goalType) || !KNOWLEDGE_STATUSES.contains(status)) {
            return null;
        }
        String corpusVersion = node.path("corpusVersion").asText(null);
        if (corpusVersion != null
                && !corpusVersion.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")) {
            corpusVersion = null;
        }
        java.util.LinkedHashSet<String> domains = new java.util.LinkedHashSet<>();
        JsonNode domainNodes = node.path("knowledgeDomains");
        if (domainNodes.isArray()) {
            for (JsonNode domainNode : domainNodes) {
                String domain = domainNode.asText(null);
                if (KNOWLEDGE_DOMAINS.contains(domain)) {
                    domains.add(domain);
                }
            }
        }
        int evidenceCount = node.path("evidenceCount").canConvertToInt()
                ? Math.max(0, Math.min(10, node.path("evidenceCount").asInt()))
                : 0;
        boolean degraded = node.path("degraded").isBoolean() && node.path("degraded").asBoolean();
        return new KnowledgeAuditSnapshot(
                goalType,
                status,
                corpusVersion,
                String.join(",", domains),
                evidenceCount,
                degraded);
    }

    private String safeAuditIdentifier(String value) {
        return value != null && value.matches("[a-z][a-z0-9_]{0,79}") ? value : null;
    }

    private String safeErrorMessage(String code) {
        if (code == null) {
            return UNAVAILABLE_MESSAGE;
        }
        return switch (code) {
            case "UPSTREAM_UNAUTHORIZED" -> "当前登录会话无法完成该查询，请重新登录后重试。";
            case "UPSTREAM_PERMISSION_DENIED" -> "当前用户没有执行该只读查询的权限。";
            case "UPSTREAM_TIMEOUT" -> "查询仓储数据超时，请稍后重试。";
            case "RAG_UNAVAILABLE" -> "知识库当前不可用，请稍后重试。实时库存、库位、托盘和化验查询不受影响。";
            case "UPSTREAM_BAD_REQUEST" -> "知识查询条件不符合要求，请换一种更明确的问法。";
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

    private String safeLimitedText(String value, String fallback, int maxLength) {
        String safe = safeText(value, fallback);
        if (safe == null || safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength);
    }

    private String safeKnowledgeText(String value, String fallback, int maxLength) {
        String safe = safeLimitedText(value, fallback, maxLength);
        if (safe == null || UNSAFE_KNOWLEDGE_TEXT.matcher(safe).find()) {
            return fallback;
        }
        return safe;
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

    private static class StreamClientDisconnectedException extends RuntimeException {
    }

    private static class InvalidStreamEventException extends RuntimeException {
    }

    private record KnowledgeAuditSnapshot(
            String goalType,
            String status,
            String corpusVersion,
            String knowledgeDomains,
            int evidenceCount,
            boolean degraded) {
    }

    private final class ActiveStream {
        private final String agentSessionId;
        private final String messageId;
        private final SseEmitter emitter;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicInteger sequence = new AtomicInteger(0);
        private final Set<String> interruptIds = ConcurrentHashMap.newKeySet();
        private volatile String resultCode = "COMPLETED";
        private volatile String agentHandoffSummary;
        private volatile KnowledgeAuditSnapshot knowledgeAuditSnapshot;

        private ActiveStream(String agentSessionId, String messageId, SseEmitter emitter) {
            this.agentSessionId = agentSessionId;
            this.messageId = messageId;
            this.emitter = emitter;
        }

        private void observe(PythonAgentStreamEventDTO event) {
            JsonNode payload = event.getPayload();
            if ("error".equals(event.getType()) && payload != null) {
                String category = payload.path("category").asText();
                if ("TOOL_TIMEOUT".equals(category)) {
                    resultCode = "TOOL_TIMEOUT";
                } else if ("TOOL_CANCELLED".equals(category)) {
                    resultCode = "TOOL_CANCELLED";
                } else if ("MODEL_TIMEOUT".equals(category)) {
                    resultCode = "MODEL_TIMEOUT";
                } else if ("MODEL_CANCELLED".equals(category)) {
                    resultCode = "MODEL_CANCELLED";
                } else if ("PYTHON_TIMEOUT".equals(category)) {
                    resultCode = "PYTHON_TIMEOUT";
                } else if ("UPSTREAM_ERROR".equals(category)) {
                    resultCode = "UPSTREAM_ERROR";
                } else {
                    resultCode = "TOOL_ERROR";
                }
            }
            if ("message_end".equals(event.getType()) && payload != null) {
                String finishReason = payload.path("finishReason").asText();
                if ("timeout".equals(finishReason) && "COMPLETED".equals(resultCode)) {
                    resultCode = "PYTHON_TIMEOUT";
                } else if ("error".equals(finishReason) && "COMPLETED".equals(resultCode)) {
                    resultCode = "UPSTREAM_ERROR";
                } else if ("cancelled".equals(finishReason)) {
                    if ("COMPLETED".equals(resultCode)) {
                        resultCode = "MODEL_CANCELLED";
                    }
                    cancelled.set(true);
                }
            }
        }

        private void recordAgentHandoff(JsonNode payload) {
            if (payload == null || payload.isNull()) {
                return;
            }
            JsonNode trace = payload.path("intentRouter");
            String summary = handoffSummaryFromTrace(trace);
            if (summary != null) {
                agentHandoffSummary = summary;
            }
            KnowledgeAuditSnapshot knowledge = knowledgeAuditFromTrace(trace);
            if (knowledge != null) {
                knowledgeAuditSnapshot = knowledge;
            }
        }

        private String agentHandoffSummary() {
            return agentHandoffSummary;
        }

        private KnowledgeAuditSnapshot knowledgeAuditSnapshot() {
            return knowledgeAuditSnapshot;
        }

        private void cancel() {
            cancelled.set(true);
            resultCode = "CLIENT_CANCELLED";
        }

        private void timeout() {
            cancelled.set(true);
            resultCode = "PYTHON_TIMEOUT";
        }

        private void recordInterrupt(String interruptId) {
            interruptIds.add(interruptId);
        }

        private boolean hasInterrupt(String interruptId) {
            return interruptIds.contains(interruptId);
        }

        private void close() {
            if (closed.compareAndSet(false, true)) {
                emitter.complete();
            }
        }

        private int nextSequence() {
            return sequence.incrementAndGet();
        }

        private void updateSequence(Integer value) {
            if (value != null) {
                sequence.accumulateAndGet(value, Math::max);
            }
        }

        private String agentSessionId() {
            return agentSessionId;
        }

        private String messageId() {
            return messageId;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private boolean isClosed() {
            return closed.get();
        }

        private String resultCode() {
            return resultCode;
        }
    }
}
