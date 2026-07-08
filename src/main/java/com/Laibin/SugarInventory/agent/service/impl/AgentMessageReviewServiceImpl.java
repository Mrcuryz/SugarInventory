package com.Laibin.SugarInventory.agent.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewQueryDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewStatusUpdateDTO;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewDetailVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewEvidenceVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewListVO;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.AgentMessageReview;
import com.Laibin.SugarInventory.domain.po.AgentMessageReviewEvidence;
import com.Laibin.SugarInventory.domain.po.AgentToolAuditLog;
import com.Laibin.SugarInventory.domain.po.AgentInterruptState;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewEvidenceMapper;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewMapper;
import com.Laibin.SugarInventory.mapper.AgentToolAuditLogMapper;
import com.Laibin.SugarInventory.mapper.AgentInterruptStateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentMessageReviewServiceImpl implements AgentMessageReviewService {
    private static final int SAFE_TEXT_LIMIT = 10000;
    private static final Set<String> REVIEW_STATUSES = Set.of("OPEN", "TRIAGED", "FIXED", "WONT_FIX");
    private static final Set<String> TEST_CASE_STATUSES = Set.of("NONE", "NEEDED", "CREATED", "PASSING");

    private final AgentMessageReviewMapper reviewMapper;
    private final AgentMessageReviewEvidenceMapper evidenceMapper;
    private final AgentToolAuditLogMapper toolAuditLogMapper;
    private final AgentInterruptStateMapper interruptStateMapper;
    private final AgentSessionService agentSessionService;
    private final ObjectMapper objectMapper;

    public AgentMessageReviewServiceImpl(AgentMessageReviewMapper reviewMapper,
                                         AgentMessageReviewEvidenceMapper evidenceMapper,
                                         AgentToolAuditLogMapper toolAuditLogMapper,
                                         AgentInterruptStateMapper interruptStateMapper,
                                         AgentSessionService agentSessionService,
                                         ObjectMapper objectMapper) {
        this.reviewMapper = reviewMapper;
        this.evidenceMapper = evidenceMapper;
        this.toolAuditLogMapper = toolAuditLogMapper;
        this.interruptStateMapper = interruptStateMapper;
        this.agentSessionService = agentSessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentMessageReview recordAssistantTurn(LoginUser loginUser, String agentSessionId, AgentMessageReviewRecordDTO dto) {
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentMessageReview review = findExisting(agentSessionId, dto.getMessageId());
        boolean exists = review != null;
        AuditEvidenceContext evidenceContext = loadEvidenceContext(agentSessionId, dto.getMessageId(),
                review == null ? null : review.getCreatedAt());
        if (!exists) {
            review = new AgentMessageReview();
            review.setAgentSessionId(limit(agentSessionId, 80));
            review.setMessageId(limit(dto.getMessageId(), 100));
            review.setUserId(loginUser.getUser().getId());
            review.setReviewSource("AUTO");
            review.setReviewStatus("OPEN");
            review.setSeverity("LOW");
            review.setTestCaseStatus("NONE");
            review.setCreatedAt(LocalDateTime.now());
        }

        review.setPagePath(limit(dto.getPagePath(), 255));
        review.setUserQuestion(limit(clean(dto.getUserQuestion()), 1000));
        review.setAssistantAnswerTextSafe(limit(clean(dto.getAssistantAnswerTextSafe()), SAFE_TEXT_LIMIT));
        review.setAssistantAnswerSummary(summary(dto));
        String actualToolNames = resolveActualToolNames(dto, evidenceContext);
        review.setActualToolNames(actualToolNames);
        review.setActualIntentSummary(limit(buildIntentSummary(dto), 1000));
        applyAutomaticClassification(review, dto, evidenceContext, actualToolNames);
        review.setAnswerTraceSummary(buildTraceSummary(review, dto, evidenceContext, actualToolNames));
        review.setAgentDecisionSnapshot(buildDecisionSnapshot(review, dto, evidenceContext, actualToolNames));
        review.setUpdatedAt(LocalDateTime.now());

        if (exists) {
            reviewMapper.updateById(review);
        } else {
            reviewMapper.insert(review);
        }
        insertEvidence(review.getId(), "AUTO_HEURISTIC", review.getMessageId(), buildAutoEvidence(review, dto, evidenceContext, actualToolNames));
        insertEvidence(review.getId(), "INTENT_ROUTER", review.getMessageId(), buildIntentRouterEvidence(dto, actualToolNames));
        insertAuditEvidence(review, evidenceContext);
        return review;
    }

    @Override
    public AgentMessageReview submitFeedback(LoginUser loginUser, String agentSessionId, String messageId, AgentMessageReviewFeedbackDTO dto) {
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentMessageReview review = findExisting(agentSessionId, messageId);
        boolean exists = review != null;
        if (!exists) {
            review = new AgentMessageReview();
            review.setAgentSessionId(limit(agentSessionId, 80));
            review.setMessageId(limit(messageId, 100));
            review.setUserId(loginUser.getUser().getId());
            review.setCreatedAt(LocalDateTime.now());
            review.setTestCaseStatus("NONE");
        }
        review.setReviewSource("USER_FEEDBACK");
        review.setReviewStatus("OPEN");
        review.setAnswerStatus("NEEDS_REVIEW");
        review.setConfidenceLevel("LOW");
        review.setSeverity("MEDIUM");
        review.setUserFeedbackType(normalize(dto.getFeedbackType(), "OTHER", 60));
        review.setUserFeedbackNote(limit(clean(dto.getFeedbackNote()), 1000));
        review.setExpectedIntentSummary(limit(clean(dto.getExpectedIntentSummary()), 1000));
        review.setExpectedCapability(limit(clean(dto.getExpectedCapability()), 200));
        review.setExpectedToolNames(joinToolNames(dto.getExpectedToolNames()));
        classifyFeedback(review, dto.getFeedbackType());
        AuditEvidenceContext evidenceContext = loadEvidenceContext(agentSessionId, messageId, review.getCreatedAt());
        review.setAnswerTraceSummary(buildTraceSummary(review, null, evidenceContext, review.getActualToolNames()));
        review.setAgentDecisionSnapshot(buildDecisionSnapshot(review, null, evidenceContext, review.getActualToolNames()));
        review.setUpdatedAt(LocalDateTime.now());

        if (exists) {
            reviewMapper.updateById(review);
        } else {
            reviewMapper.insert(review);
        }
        insertEvidence(review.getId(), "USER_FEEDBACK", messageId, buildFeedbackEvidence(dto));
        insertAuditEvidence(review, evidenceContext);
        return review;
    }

    @Override
    public PageResult<AgentMessageReviewListVO> pageReviews(LoginUser loginUser, AgentMessageReviewQueryDTO query) {
        requireReviewAdmin(loginUser, "agent:review:view");
        int pageNo = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getSize() == null || query.getSize() < 1 ? 20 : Math.min(query.getSize(), 100);
        LambdaQueryWrapper<AgentMessageReview> wrapper = new LambdaQueryWrapper<AgentMessageReview>()
                .eq(StringUtils.hasText(query.getReviewStatus()), AgentMessageReview::getReviewStatus, normalize(query.getReviewStatus(), null, 40))
                .eq(StringUtils.hasText(query.getAnswerStatus()), AgentMessageReview::getAnswerStatus, normalize(query.getAnswerStatus(), null, 40))
                .eq(StringUtils.hasText(query.getFailureDomain()), AgentMessageReview::getFailureDomain, normalize(query.getFailureDomain(), null, 80))
                .eq(StringUtils.hasText(query.getFailureCategory()), AgentMessageReview::getFailureCategory, normalize(query.getFailureCategory(), null, 120))
                .eq(StringUtils.hasText(query.getSuggestedFixType()), AgentMessageReview::getSuggestedFixType, normalize(query.getSuggestedFixType(), null, 80))
                .eq(StringUtils.hasText(query.getTestCaseStatus()), AgentMessageReview::getTestCaseStatus, normalize(query.getTestCaseStatus(), null, 40));
        if (Boolean.TRUE.equals(query.getPriorityOnly())) {
            wrapper.and(nested -> nested
                    .in(AgentMessageReview::getAnswerStatus, List.of("NEEDS_REVIEW", "LOW_CONFIDENCE", "FAILED"))
                    .or()
                    .eq(AgentMessageReview::getReviewSource, "USER_FEEDBACK")
                    .or()
                    .isNotNull(AgentMessageReview::getFailureDomain)
                    .ne(AgentMessageReview::getFailureDomain, ""));
        }
        wrapper.orderByDesc(AgentMessageReview::getCreatedAt)
                .orderByDesc(AgentMessageReview::getId);
        Page<AgentMessageReview> page = reviewMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<AgentMessageReviewListVO> records = page.getRecords().stream()
                .map(this::toListVO)
                .toList();
        return new PageResult<>(page.getTotal(), records);
    }

    @Override
    public AgentMessageReviewDetailVO getReviewDetail(LoginUser loginUser, Long id) {
        requireReviewAdmin(loginUser, "agent:review:view");
        AgentMessageReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(404, "Review record not found.");
        }
        List<AgentMessageReviewEvidence> evidence = evidenceMapper.selectList(new LambdaQueryWrapper<AgentMessageReviewEvidence>()
                .eq(AgentMessageReviewEvidence::getReviewId, id)
                .orderByAsc(AgentMessageReviewEvidence::getCreatedAt)
                .orderByAsc(AgentMessageReviewEvidence::getId));
        return toDetailVO(review, evidence);
    }

    @Override
    public AgentMessageReviewDetailVO updateReviewStatus(LoginUser loginUser, Long id, AgentMessageReviewStatusUpdateDTO dto) {
        requireReviewAdmin(loginUser, "agent:review:update");
        AgentMessageReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(404, "Review record not found.");
        }
        String reviewStatus = normalize(dto.getReviewStatus(), null, 40);
        String testCaseStatus = normalize(dto.getTestCaseStatus(), null, 40);
        if (StringUtils.hasText(reviewStatus)) {
            if ("TEST_NEEDED".equals(reviewStatus)) {
                testCaseStatus = "NEEDED";
            } else if (!REVIEW_STATUSES.contains(reviewStatus)) {
                throw new BusinessException(400, "Unsupported review status.");
            } else {
                review.setReviewStatus(reviewStatus);
            }
        }
        if (StringUtils.hasText(testCaseStatus)) {
            if (!TEST_CASE_STATUSES.contains(testCaseStatus)) {
                throw new BusinessException(400, "Unsupported test case status.");
            }
            review.setTestCaseStatus(testCaseStatus);
        }
        if (dto.getAdminNote() != null) {
            review.setReviewNote(limit(clean(dto.getAdminNote()), 1000));
        }
        review.setReviewedBy(loginUser.getUser().getId());
        review.setUpdatedAt(LocalDateTime.now());
        reviewMapper.updateById(review);
        List<AgentMessageReviewEvidence> evidence = evidenceMapper.selectList(new LambdaQueryWrapper<AgentMessageReviewEvidence>()
                .eq(AgentMessageReviewEvidence::getReviewId, id)
                .orderByAsc(AgentMessageReviewEvidence::getCreatedAt)
                .orderByAsc(AgentMessageReviewEvidence::getId));
        return toDetailVO(review, evidence);
    }

    private AgentMessageReview findExisting(String agentSessionId, String messageId) {
        if (!StringUtils.hasText(agentSessionId) || !StringUtils.hasText(messageId)) {
            return null;
        }
        return reviewMapper.selectOne(new LambdaQueryWrapper<AgentMessageReview>()
                .eq(AgentMessageReview::getAgentSessionId, agentSessionId)
                .eq(AgentMessageReview::getMessageId, messageId)
                .last("LIMIT 1"));
    }

    private AgentMessageReviewListVO toListVO(AgentMessageReview review) {
        Map<String, Object> snapshot = reviewSnapshot(review);
        AgentMessageReviewListVO vo = new AgentMessageReviewListVO();
        vo.setId(review.getId());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setUserQuestionSummary(shortText(review.getUserQuestion(), 160));
        vo.setAssistantAnswerSummary(shortText(firstNonBlank(review.getAssistantAnswerSummary(), review.getAssistantAnswerTextSafe()), 220));
        vo.setAnswerStatus(clean(review.getAnswerStatus()));
        vo.setConfidenceLevel(clean(review.getConfidenceLevel()));
        vo.setFailureDomain(clean(review.getFailureDomain()));
        vo.setFailureCategory(clean(review.getFailureCategory()));
        vo.setSuggestedFixType(clean(review.getSuggestedFixType()));
        vo.setIntentType(safeTextValue(snapshot.get("intent_type"), detectIntentType(review.getUserQuestion()), 80));
        vo.setPlannedTools(safeListValue(snapshot.get("planned_tools")));
        vo.setActualToolNames(new ArrayList<>(splitTools(review.getActualToolNames())));
        vo.setReviewStatus(clean(review.getReviewStatus()));
        vo.setTestCaseStatus(clean(review.getTestCaseStatus()));
        return vo;
    }

    private AgentMessageReviewDetailVO toDetailVO(AgentMessageReview review, List<AgentMessageReviewEvidence> evidence) {
        Map<String, Object> snapshot = reviewSnapshot(review);
        AgentMessageReviewDetailVO vo = new AgentMessageReviewDetailVO();
        vo.setId(review.getId());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setAgentSessionId(clean(review.getAgentSessionId()));
        vo.setMessageId(clean(review.getMessageId()));
        vo.setUserQuestion(limit(clean(review.getUserQuestion()), 1000));
        vo.setAssistantAnswerTextSafe(limit(clean(review.getAssistantAnswerTextSafe()), SAFE_TEXT_LIMIT));
        vo.setAssistantAnswerSummary(limit(clean(firstNonBlank(review.getAssistantAnswerSummary(), review.getAssistantAnswerTextSafe())), 1000));
        vo.setAnswerTraceSummary(limit(safeTraceSummary(review), 1000));
        vo.setAgentDecisionSnapshot(snapshot);
        vo.setAnswerStatus(clean(review.getAnswerStatus()));
        vo.setConfidenceLevel(clean(review.getConfidenceLevel()));
        vo.setFailureDomain(clean(review.getFailureDomain()));
        vo.setFailureCategory(clean(review.getFailureCategory()));
        vo.setSuggestedFixType(clean(review.getSuggestedFixType()));
        vo.setReviewStatus(clean(review.getReviewStatus()));
        vo.setTestCaseStatus(clean(review.getTestCaseStatus()));
        vo.setUserFeedbackType(clean(review.getUserFeedbackType()));
        vo.setUserFeedbackNote(limit(clean(review.getUserFeedbackNote()), 1000));
        vo.setExpectedIntentSummary(limit(clean(review.getExpectedIntentSummary()), 1000));
        vo.setActualIntentSummary(limit(clean(review.getActualIntentSummary()), 1000));
        vo.setPlannedTools(safeListValue(snapshot.get("planned_tools")));
        vo.setActualToolNames(new ArrayList<>(splitTools(review.getActualToolNames())));
        vo.setAdminNote(limit(clean(review.getReviewNote()), 1000));
        vo.setEvidenceSummary((evidence == null ? List.<AgentMessageReviewEvidence>of() : evidence).stream()
                .map(this::toEvidenceVO)
                .toList());
        return vo;
    }

    private AgentMessageReviewEvidenceVO toEvidenceVO(AgentMessageReviewEvidence evidence) {
        AgentMessageReviewEvidenceVO vo = new AgentMessageReviewEvidenceVO();
        vo.setId(evidence.getId());
        vo.setEvidenceType(clean(evidence.getEvidenceType()));
        vo.setRefId(clean(evidence.getRefId()));
        vo.setEvidenceSummary(limit(clean(evidence.getEvidenceSummary()), 1000));
        vo.setCreatedAt(evidence.getCreatedAt());
        return vo;
    }

    private void requireReviewAdmin(LoginUser loginUser, String requiredAuthority) {
        if (loginUser == null || loginUser.getUser() == null) {
            throw new BusinessException(401, "Login required.");
        }
        String roleCode = loginUser.getUser().getRoleCode();
        boolean allowedRole = "ADMIN".equalsIgnoreCase(roleCode) || "SUPER_ADMIN".equalsIgnoreCase(roleCode);
        boolean allowedAuthority = loginUser.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(authority -> requiredAuthority.equals(authority)
                        || "ROLE_ADMIN".equals(authority)
                        || "ROLE_SUPER_ADMIN".equals(authority));
        if (!allowedRole && !allowedAuthority) {
            throw new BusinessException(403, "Agent review permission is required.");
        }
    }

    private Map<String, Object> safeDecisionSnapshot(String snapshotJson) {
        if (!StringUtils.hasText(snapshotJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(snapshotJson, new TypeReference<>() {});
            Object safe = safeStructuredValue(parsed);
            if (safe instanceof Map<?, ?> safeMap) {
                Map<String, Object> result = new LinkedHashMap<>();
                safeMap.forEach((key, value) -> result.put(String.valueOf(key), value));
                return result;
            }
        } catch (JsonProcessingException ignored) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> reviewSnapshot(AgentMessageReview review) {
        Map<String, Object> snapshot = safeDecisionSnapshot(review.getAgentDecisionSnapshot());
        String fallbackIntent = detectIntentType(review.getUserQuestion());
        String intent = safeTextValue(snapshot.get("intent_type"), null, 80);
        if (!StringUtils.hasText(intent) || isUnknownValue(intent)) {
            intent = fallbackIntent;
            if (StringUtils.hasText(intent)) {
                snapshot.put("intent_type", intent);
            }
        }
        if ("write_operation".equals(intent)) {
            snapshot.put("intent_subtype", fallbackIntentSubtype(intent, review.getUserQuestion()));
            snapshot.put("business_domain", detectBusinessDomain(review.getUserQuestion()));
            snapshot.put("missing_slots", List.of());
            snapshot.put("support_status", fallbackSupportStatus(intent));
            snapshot.put("next_action", fallbackNextAction(intent));
            snapshot.put("planned_tools", List.of());
            snapshot.put("actual_tools", new ArrayList<>(splitTools(review.getActualToolNames())));
            return snapshot;
        }
        String supportStatus = safeTextValue(snapshot.get("support_status"), null, 80);
        if (isUnknownValue(supportStatus) || "COMPLETED".equals(supportStatus)) {
            String fallback = fallbackSupportStatus(intent);
            if (StringUtils.hasText(fallback)) {
                snapshot.put("support_status", fallback);
            } else {
                snapshot.remove("support_status");
            }
        }
        return snapshot;
    }

    private String safeTraceSummary(AgentMessageReview review) {
        String trace = clean(review.getAnswerTraceSummary());
        if (!StringUtils.hasText(trace)
                || trace.contains("UNKNOWN")
                || trace.contains("intentType=unknown")
                || trace.contains("supportStatus=COMPLETED")) {
            return buildTraceSummary(review, null, AuditEvidenceContext.empty(), review.getActualToolNames());
        }
        return trace;
    }

    private List<String> safeListValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> safeTextValue(item, null, 100))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        String scalar = safeTextValue(value, null, 100);
        return StringUtils.hasText(scalar) ? List.of(scalar) : List.of();
    }

    private String safeTextValue(Object value, String fallback, int maxLength) {
        if (value == null) {
            return fallback;
        }
        String safe = safeScalar(String.valueOf(value), maxLength);
        return StringUtils.hasText(safe) ? safe : fallback;
    }

    private boolean isUnknownValue(String value) {
        return value == null || "UNKNOWN".equalsIgnoreCase(value) || "-".equals(value);
    }

    private List<String> safeMissingSlots(AgentMessageReviewRecordDTO dto,
                                          AgentMessageReview review,
                                          String question,
                                          String intentType) {
        if (dto != null && hasRouterSnapshot(dto)) {
            return safeStringList(dto.getMissingSlots(), 100);
        }
        if ("write_operation".equals(intentType) || "capability".equals(intentType) || "smalltalk".equals(intentType)) {
            return List.of();
        }
        return missingSlots(review, question);
    }

    private boolean hasRouterSnapshot(AgentMessageReviewRecordDTO dto) {
        return StringUtils.hasText(dto.getIntentType())
                || StringUtils.hasText(dto.getIntentSubtype())
                || StringUtils.hasText(dto.getBusinessDomain())
                || StringUtils.hasText(dto.getSupportStatus())
                || StringUtils.hasText(dto.getNextAction())
                || !safeStringList(dto.getPlannedTools(), 100).isEmpty();
    }

    private void applyAutomaticClassification(AgentMessageReview review,
                                              AgentMessageReviewRecordDTO dto,
                                              AuditEvidenceContext evidenceContext,
                                              String actualToolNames) {
        String providedStatus = normalize(dto.getAnswerStatus(), null, 40);
        String providedConfidence = normalize(dto.getConfidenceLevel(), null, 20);
        String question = nullToEmpty(dto.getUserQuestion());
        String answer = nullToEmpty(dto.getAssistantAnswerTextSafe());
        boolean businessQuestion = containsAny(question, "库存", "库位", "托盘", "化验", "不合格", "无标准", "二维码");
        Set<String> tools = splitTools(actualToolNames);
        Set<String> plannedTools = safeToolSet(dto.getPlannedTools());
        boolean noTool = tools.isEmpty();
        boolean resolverOnly = !noTool && tools.stream().allMatch(this::isResolverTool);
        boolean plannedMainTool = plannedTools.stream().anyMatch(tool -> !isResolverTool(tool));
        boolean toolExpected = plannedMainTool
                && "call_tool".equals(normalizeLower(dto.getNextAction()))
                && !"ambiguous".equals(normalizeLower(dto.getSupportStatus()));
        boolean fallback = containsAny(answer, "未找到匹配", "暂不支持", "无法准确", "请换一个更准确", "没有执行", "没有查询");
        boolean unsupportedCapability = isUnsupportedCapability(dto, question, answer);
        boolean noDataAnswer = containsAny(answer, "未找到", "没有查询到", "没有库存", "暂无库存", "无库存", "没有符合条件");
        boolean nonEmptyToolResult = evidenceContext.hasNonEmptyMainToolResult();
        boolean smallTalkMisrouted = isSmallTalkOrCapability(dto)
                && containsAny(answer, "请补充要查询的产品", "请补充要查询的产品、库位", "库位、托盘码");
        boolean contextFollowup = containsAny(question, "这些", "它", "刚才", "这个", "该产品", "上述", "前面");
        boolean contextNotUsed = contextFollowup
                && (noTool || resolverOnly || containsAny(answer, "请先选择或输入一个明确", "请提供明确产品名称"));

        if ("cancelled".equalsIgnoreCase(dto.getFinishReason())) {
            review.setAnswerStatus("CANCELLED");
            review.setConfidenceLevel("UNKNOWN");
            review.setFailureDomain("SYSTEM");
            review.setFailureCategory("CLIENT_CANCELLED");
            return;
        }
        if (smallTalkMisrouted) {
            review.setAnswerStatus("NEEDS_REVIEW");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("PLANNER");
            review.setFailureCategory("INTENT_MISS");
            review.setSuggestedFixType("FIX_PROMPT");
            review.setTestCaseStatus("NEEDED");
            return;
        }
        if (unsupportedCapability) {
            review.setAnswerStatus("NEEDS_REVIEW");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("PLANNER");
            review.setFailureCategory("CAPABILITY_UNSUPPORTED");
            review.setSuggestedFixType("FIX_PLANNER");
            review.setTestCaseStatus("NEEDED");
            return;
        }
        if (nonEmptyToolResult && noDataAnswer) {
            review.setAnswerStatus("NEEDS_REVIEW");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("SAFE_ADAPTER");
            review.setFailureCategory("NON_EMPTY_RESULT_RENDERED_AS_EMPTY");
            review.setSuggestedFixType("FIX_SAFE_ADAPTER");
            review.setTestCaseStatus("NEEDED");
            return;
        }
        if (contextNotUsed) {
            review.setAnswerStatus("LOW_CONFIDENCE");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("CONTEXT");
            review.setFailureCategory("CONTEXT_NOT_USED");
            review.setSuggestedFixType("FIX_PLANNER");
            review.setTestCaseStatus("NEEDED");
            return;
        }
        if ((businessQuestion || toolExpected) && resolverOnly) {
            review.setAnswerStatus("LOW_CONFIDENCE");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("PLANNER");
            review.setFailureCategory("RESOLVER_ONLY");
            review.setSuggestedFixType("FIX_PLANNER");
            review.setTestCaseStatus("NEEDED");
            return;
        }
        if (fallback) {
            review.setAnswerStatus("LOW_CONFIDENCE");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("DATA");
            review.setFailureCategory("NO_MATCH_OR_FALLBACK");
            review.setSuggestedFixType("FIX_PLANNER");
            return;
        }
        if ((businessQuestion || toolExpected) && noTool && !"ask_clarification".equals(normalizeLower(dto.getNextAction()))) {
            review.setAnswerStatus("LOW_CONFIDENCE");
            review.setConfidenceLevel("LOW");
            review.setFailureDomain("PLANNER");
            review.setFailureCategory("TOOL_NOT_CALLED");
            review.setSuggestedFixType("FIX_PLANNER");
            return;
        }
        review.setAnswerStatus(providedStatus != null ? providedStatus : "COMPLETED");
        review.setConfidenceLevel(providedConfidence != null ? providedConfidence : "MEDIUM");
        review.setFailureDomain(null);
        review.setFailureCategory(null);
    }

    private void classifyFeedback(AgentMessageReview review, String feedbackType) {
        String type = normalize(feedbackType, "OTHER", 60);
        switch (type) {
            case "WRONG_INTENT" -> {
                review.setFailureDomain("PLANNER");
                review.setFailureCategory("INTENT_MISS");
                review.setSuggestedFixType("FIX_PLANNER");
                review.setTestCaseStatus("NEEDED");
            }
            case "DATA_WRONG" -> {
                review.setFailureDomain("DATA");
                review.setFailureCategory("DATA_MISMATCH");
                review.setSuggestedFixType("FIX_DATA_MODEL");
                review.setTestCaseStatus("NEEDED");
            }
            case "NO_TOOL" -> {
                review.setFailureDomain("PLANNER");
                review.setFailureCategory("TOOL_NOT_CALLED");
                review.setSuggestedFixType("FIX_PLANNER");
                review.setTestCaseStatus("NEEDED");
            }
            case "CARD_BAD" -> {
                review.setFailureDomain("UI");
                review.setFailureCategory("CARD_RENDER_BAD");
                review.setSuggestedFixType("FIX_UI_RENDER");
            }
            case "UNCLEAR" -> {
                review.setFailureDomain("MODEL");
                review.setFailureCategory("ANSWER_UNCLEAR");
                review.setSuggestedFixType("FIX_PROMPT");
            }
            case "USER_CORRECTION" -> {
                review.setFailureDomain("USER_INPUT");
                review.setFailureCategory("USER_CORRECTION_DETECTED");
                review.setSuggestedFixType("FIX_PLANNER");
                review.setTestCaseStatus("NEEDED");
            }
            default -> {
                review.setFailureDomain("UNKNOWN");
                review.setFailureCategory("USER_FEEDBACK");
            }
        }
    }

    private void insertEvidence(Long reviewId, String evidenceType, String refId, String summary) {
        if (reviewId == null || !StringUtils.hasText(summary)) {
            return;
        }
        AgentMessageReviewEvidence evidence = new AgentMessageReviewEvidence();
        evidence.setReviewId(reviewId);
        evidence.setEvidenceType(evidenceType);
        evidence.setRefId(limit(refId, 100));
        evidence.setEvidenceSummary(limit(clean(summary), 1000));
        evidence.setCreatedAt(LocalDateTime.now());
        evidenceMapper.insert(evidence);
    }

    private String buildAutoEvidence(AgentMessageReview review,
                                     AgentMessageReviewRecordDTO dto,
                                     AuditEvidenceContext evidenceContext,
                                     String actualToolNames) {
        String question = dto == null ? review.getUserQuestion() : dto.getUserQuestion();
        String tools = actualToolNames;
        if (!StringUtils.hasText(tools)) {
            return "用户询问：" + shortText(question, 120)
                    + "；本轮未记录实际工具调用；疑似 "
                    + nullToUnknown(review.getFailureDomain()) + " / " + nullToUnknown(review.getFailureCategory()) + "。";
        }
        if (splitTools(tools).stream().allMatch(this::isResolverTool)) {
            return "用户询问：" + shortText(question, 120)
                    + "；本轮仅调用 resolver 工具：" + tools
                    + "，未继续调用主查询工具；疑似 "
                    + nullToUnknown(review.getFailureDomain()) + " / " + nullToUnknown(review.getFailureCategory()) + "。";
        }
        if (evidenceContext.hasNonEmptyMainToolResult()
                && containsAny(review.getAssistantAnswerTextSafe(), "未找到", "没有查询到", "无库存", "暂无库存")) {
            return "本轮主查询工具结果摘要显示非空，但助手回答为无数据；疑似 SAFE_ADAPTER / NON_EMPTY_RESULT_RENDERED_AS_EMPTY。";
        }
        return "本轮实际调用工具：" + tools + "；自动归因："
                + nullToUnknown(review.getFailureDomain()) + " / " + nullToUnknown(review.getFailureCategory())
                + "；answerStatus=" + review.getAnswerStatus() + "。";
    }

    private String buildFeedbackEvidence(AgentMessageReviewFeedbackDTO dto) {
        String note = clean(dto.getFeedbackNote());
        if (StringUtils.hasText(note)) {
            return "用户反馈：" + normalize(dto.getFeedbackType(), "OTHER", 60) + "；" + note;
        }
        return "用户反馈：" + normalize(dto.getFeedbackType(), "OTHER", 60);
    }

    private String summary(AgentMessageReviewRecordDTO dto) {
        if (StringUtils.hasText(dto.getAssistantAnswerSummary())) {
            return limit(clean(dto.getAssistantAnswerSummary()), 1000);
        }
        return limit(clean(dto.getAssistantAnswerTextSafe()), 300);
    }

    private String buildIntentSummary(AgentMessageReviewRecordDTO dto) {
        if (dto == null) {
            return null;
        }
        String provided = clean(dto.getActualIntentSummary());
        String router = buildIntentRouterEvidence(dto, null);
        if (StringUtils.hasText(provided) && StringUtils.hasText(router)) {
            return limit(provided + "；" + router, 1000);
        }
        return StringUtils.hasText(provided) ? provided : router;
    }

    private String buildIntentRouterEvidence(AgentMessageReviewRecordDTO dto, String actualToolNames) {
        if (dto == null || !StringUtils.hasText(dto.getIntentType())) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add("intent_type=" + safeScalar(dto.getIntentType(), 80));
        addPart(parts, "intent_subtype", dto.getIntentSubtype(), 120);
        addPart(parts, "business_domain", dto.getBusinessDomain(), 80);
        addPart(parts, "support_status", dto.getSupportStatus(), 80);
        addPart(parts, "next_action", dto.getNextAction(), 80);
        String missingSlots = joinToolNames(dto.getMissingSlots());
        if (StringUtils.hasText(missingSlots)) {
            parts.add("missing_slots=" + missingSlots);
        }
        String plannedTools = joinToolNames(dto.getPlannedTools());
        if (StringUtils.hasText(plannedTools)) {
            parts.add("planned_tools=" + plannedTools);
        }
        if (StringUtils.hasText(actualToolNames)) {
            parts.add("actual_tools=" + actualToolNames);
        }
        Map<String, Object> objects = safeBusinessObjects(dto.getBusinessObjects());
        if (!objects.isEmpty()) {
            parts.add("business_objects=" + safeBusinessObjectSummary(objects));
        }
        return limit("Intent router 摘要：" + String.join("；", parts), 1000);
    }

    private AuditEvidenceContext loadEvidenceContext(String agentSessionId, String messageId, LocalDateTime reviewCreatedAt) {
        if (!StringUtils.hasText(agentSessionId)) {
            return AuditEvidenceContext.empty();
        }
        List<AgentToolAuditLog> audits = List.of();
        boolean weakMatch = false;
        if (StringUtils.hasText(messageId)) {
            audits = toolAuditLogMapper.selectList(new LambdaQueryWrapper<AgentToolAuditLog>()
                    .eq(AgentToolAuditLog::getAgentSessionId, agentSessionId)
                    .eq(AgentToolAuditLog::getMessageId, messageId)
                    .orderByAsc(AgentToolAuditLog::getCreatedAt)
                    .last("LIMIT 30"));
        }
        if ((audits == null || audits.isEmpty()) && reviewCreatedAt != null) {
            weakMatch = true;
            audits = toolAuditLogMapper.selectList(new LambdaQueryWrapper<AgentToolAuditLog>()
                    .eq(AgentToolAuditLog::getAgentSessionId, agentSessionId)
                    .ge(AgentToolAuditLog::getCreatedAt, reviewCreatedAt.minusMinutes(5))
                    .le(AgentToolAuditLog::getCreatedAt, reviewCreatedAt.plusMinutes(1))
                    .orderByAsc(AgentToolAuditLog::getCreatedAt)
                    .last("LIMIT 30"));
        }
        List<AgentInterruptState> interrupts = List.of();
        if (StringUtils.hasText(messageId)) {
            interrupts = interruptStateMapper.selectList(new LambdaQueryWrapper<AgentInterruptState>()
                    .eq(AgentInterruptState::getAgentSessionId, agentSessionId)
                    .eq(AgentInterruptState::getMessageId, messageId)
                    .orderByAsc(AgentInterruptState::getCreatedAt)
                    .last("LIMIT 10"));
        }
        return new AuditEvidenceContext(
                audits == null ? List.of() : audits,
                interrupts == null ? List.of() : interrupts,
                weakMatch);
    }

    private String resolveActualToolNames(AgentMessageReviewRecordDTO dto, AuditEvidenceContext evidenceContext) {
        List<String> auditedTools = evidenceContext.businessToolAudits().stream()
                .map(AgentToolAuditLog::getToolName)
                .toList();
        String audited = joinToolNames(auditedTools);
        return StringUtils.hasText(audited) ? audited : joinToolNames(dto.getActualToolNames());
    }

    private void insertAuditEvidence(AgentMessageReview review, AuditEvidenceContext evidenceContext) {
        if (review.getId() == null) {
            return;
        }
        String runtime = buildRuntimeEvidence(evidenceContext);
        if (StringUtils.hasText(runtime)) {
            insertEvidence(review.getId(), "RUNTIME_AUDIT", review.getMessageId(), runtime);
        }
        String tool = buildToolAuditEvidence(review, evidenceContext);
        if (StringUtils.hasText(tool)) {
            insertEvidence(review.getId(), "TOOL_AUDIT", review.getMessageId(), tool);
        }
        String interrupt = buildInterruptEvidence(evidenceContext);
        if (StringUtils.hasText(interrupt)) {
            insertEvidence(review.getId(), "INTERRUPT_STATE", review.getMessageId(), interrupt);
        }
    }

    private String buildRuntimeEvidence(AuditEvidenceContext evidenceContext) {
        List<AgentToolAuditLog> runtimeAudits = evidenceContext.runtimeAudits();
        if (runtimeAudits.isEmpty()) {
            return null;
        }
        AgentToolAuditLog latest = runtimeAudits.get(runtimeAudits.size() - 1);
        return matchPrefix(evidenceContext)
                + "Agent runtime 摘要：path=" + nullToUnknown(latest.getUpstreamPath())
                + "，resultCode=" + nullToUnknown(latest.getResultCode())
                + "，errorCode=" + nullToUnknown(latest.getErrorCode())
                + "，durationMs=" + latest.getDurationMs() + "。";
    }

    private String buildToolAuditEvidence(AgentMessageReview review, AuditEvidenceContext evidenceContext) {
        List<AgentToolAuditLog> toolAudits = evidenceContext.businessToolAudits();
        if (toolAudits.isEmpty()) {
            return null;
        }
        String calls = toolAudits.stream()
                .map(item -> item.getToolName()
                        + " " + nullToUnknown(item.getResultCode())
                        + (StringUtils.hasText(item.getErrorCode()) ? "/" + item.getErrorCode() : "")
                        + (item.getDurationMs() == null ? "" : " " + item.getDurationMs() + "ms"))
                .collect(Collectors.joining("；"));
        String suffix = "";
        if ("RESOLVER_ONLY".equals(review.getFailureCategory())) {
            suffix = "；本轮只有 resolver，未记录主查询工具。";
        } else if ("NON_EMPTY_RESULT_RENDERED_AS_EMPTY".equals(review.getFailureCategory())) {
            suffix = "；主查询工具审计摘要显示非空，但回答呈现为无数据。";
        }
        return matchPrefix(evidenceContext) + "本轮工具调用摘要：" + calls + suffix;
    }

    private String buildInterruptEvidence(AuditEvidenceContext evidenceContext) {
        if (evidenceContext.interrupts().isEmpty()) {
            return null;
        }
        return evidenceContext.interrupts().stream()
                .map(item -> "interruptId=" + nullToUnknown(item.getInterruptId())
                        + "，kind=" + nullToUnknown(item.getKind())
                        + "，status=" + nullToUnknown(item.getStatus())
                        + "，action=" + nullToUnknown(item.getResumeAction())
                        + "，resultCode=" + nullToUnknown(item.getResultCode()))
                .collect(Collectors.joining("；"));
    }

    private String buildTraceSummary(AgentMessageReview review,
                                     AgentMessageReviewRecordDTO dto,
                                     AuditEvidenceContext evidenceContext,
                                     String actualToolNames) {
        String question = dto == null ? review.getUserQuestion() : dto.getUserQuestion();
        String finishReason = dto == null ? null : dto.getFinishReason();
        boolean cardGenerated = dto != null && Boolean.TRUE.equals(dto.getHasCards());
        String intentType = firstNonBlank(dto == null ? null : dto.getIntentType(), detectIntentType(question));
        return limit("intentType=" + nullToDash(intentType)
                + "；intentSubtype=" + nullToDash(firstNonBlank(dto == null ? null : dto.getIntentSubtype(), fallbackIntentSubtype(intentType, question)))
                + "；businessDomain=" + nullToDash(firstNonBlank(dto == null ? null : dto.getBusinessDomain(), detectBusinessDomain(question)))
                + "；supportStatus=" + nullToDash(firstNonBlank(dto == null ? null : dto.getSupportStatus(), fallbackSupportStatus(intentType)))
                + "；nextAction=" + nullToDash(firstNonBlank(dto == null ? null : dto.getNextAction(), fallbackNextAction(intentType)))
                + "；plannedTools=" + nullToDash(joinToolNames(dto == null ? null : dto.getPlannedTools()))
                + "；actualTools=" + nullToDash(actualToolNames)
                + "；finishReason=" + nullToDash(finishReason)
                + "；cardGenerated=" + cardGenerated
                + "；classification=" + nullToDash(review.getFailureDomain()) + "/" + nullToDash(review.getFailureCategory())
                + "；auditMatch=" + (evidenceContext.weakMatch() ? "TIME_WINDOW" : "MESSAGE_ID"), 1000);
    }

    private String buildDecisionSnapshot(AgentMessageReview review,
                                         AgentMessageReviewRecordDTO dto,
                                         AuditEvidenceContext evidenceContext,
                                         String actualToolNames) {
        String question = dto == null ? review.getUserQuestion() : dto.getUserQuestion();
        String intentType = firstNonBlank(dto == null ? null : dto.getIntentType(), detectIntentType(question));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("intent_type", intentType);
        snapshot.put("intent_subtype", firstNonBlank(dto == null ? null : dto.getIntentSubtype(), fallbackIntentSubtype(intentType, question)));
        snapshot.put("business_domain", firstNonBlank(dto == null ? null : dto.getBusinessDomain(), detectBusinessDomain(question)));
        snapshot.put("business_objects", safeBusinessObjects(dto == null ? null : dto.getBusinessObjects()));
        snapshot.put("recognized_entities", recognizedEntities(question));
        snapshot.put("missing_slots", safeMissingSlots(dto, review, question, intentType));
        snapshot.put("support_status", firstNonBlank(dto == null ? null : dto.getSupportStatus(), fallbackSupportStatus(intentType)));
        snapshot.put("next_action", firstNonBlank(dto == null ? null : dto.getNextAction(), fallbackNextAction(intentType)));
        snapshot.put("planned_tools", safeStringList(dto == null ? null : dto.getPlannedTools(), 100));
        snapshot.put("actual_tools", new ArrayList<>(splitTools(actualToolNames)));
        snapshot.put("finish_reason", dto == null ? null : dto.getFinishReason());
        snapshot.put("answer_type", detectAnswerType(review));
        snapshot.put("card_generated", dto != null && Boolean.TRUE.equals(dto.getHasCards()));
        snapshot.put("audit_match", evidenceContext.weakMatch() ? "TIME_WINDOW" : "MESSAGE_ID");
        try {
            return limit(clean(objectMapper.writeValueAsString(snapshot)), 4000);
        } catch (JsonProcessingException e) {
            return limit(clean(snapshot.toString()), 4000);
        }
    }

    private Set<String> splitTools(String actualToolNames) {
        if (!StringUtils.hasText(actualToolNames)) {
            return Set.of();
        }
        return List.of(actualToolNames.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> safeToolSet(List<String> toolNames) {
        String joined = joinToolNames(toolNames);
        return splitTools(joined);
    }

    private List<String> safeStringList(List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> safeScalar(value, maxLength))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> safeListOrFallback(List<String> values, List<String> fallback) {
        List<String> safe = safeStringList(values, 100);
        return safe.isEmpty() ? fallback : safe;
    }

    private Map<String, Object> safeBusinessObjects(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String safeKey = safeScalar(key, 80);
            if (!StringUtils.hasText(safeKey) || isUnsafeKey(safeKey)) {
                return;
            }
            Object safeValue = safeStructuredValue(value);
            if (safeValue != null) {
                result.put(safeKey, safeValue);
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object safeStructuredValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((rawKey, rawValue) -> {
                String key = safeScalar(rawKey == null ? null : String.valueOf(rawKey), 80);
                if (!StringUtils.hasText(key) || isUnsafeKey(key)) {
                    return;
                }
                Object safeValue = safeStructuredValue(rawValue);
                if (safeValue != null) {
                    result.put(key, safeValue);
                }
            });
            return result.isEmpty() ? null : result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = list.stream()
                    .map(this::safeStructuredValue)
                    .filter(item -> item != null)
                    .limit(20)
                    .toList();
            return result.isEmpty() ? null : result;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return safeScalar(String.valueOf(value), 120);
    }

    private String safeBusinessObjectSummary(Map<String, Object> objects) {
        return objects.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + safeScalar(String.valueOf(entry.getValue()), 120))
                .collect(Collectors.joining("|"));
    }

    private void addPart(List<String> parts, String key, String value, int maxLength) {
        String safe = safeScalar(value, maxLength);
        if (StringUtils.hasText(safe)) {
            parts.add(key + "=" + safe);
        }
    }

    private String safeScalar(String value, int maxLength) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        return limit(cleaned.replaceAll("[\\r\\n\\t]", " "), maxLength);
    }

    private boolean isUnsafeKey(String key) {
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("stacktrace")
                || normalized.endsWith("id");
    }

    private boolean isResolverTool(String toolName) {
        return "resolve_products".equals(toolName) || "resolve_warehouses".equals(toolName);
    }

    private boolean isMainTool(String toolName) {
        return StringUtils.hasText(toolName)
                && !"agent_runtime".equals(toolName)
                && !isResolverTool(toolName);
    }

    private String detectIntentType(String text) {
        String value = nullToEmpty(text);
        if (isWriteOperationText(value)) {
            return "write_operation";
        }
        if (isUnsupportedCapabilityQuestion(value)) {
            return containsAny(value, "生产订单") ? "unsupported" : "report_analysis";
        }
        if (containsAny(value, "你是什么", "你是谁", "你能干嘛", "你现在能做什么", "你能做什么", "你可以做什么",
                "你支持什么", "你有哪些功能", "你有什么功能", "你能帮我什么", "这个助手有什么用", "介绍一下")) {
            return "capability";
        }
        if (containsAny(value, "你好", "您好", "hello", "hi")) {
            return "smalltalk";
        }
        if (containsAny(value, "这些", "它", "刚才", "这个", "该产品", "上述", "前面")) {
            return "context_followup";
        }
        if (containsAny(value, "查一下库存", "查库存", "查询库存") && !containsAny(value, "库位", "产品", "黄冰糖", "白冰糖", "托盘", "化验")) {
            return "ambiguous";
        }
        if (containsAny(value, "库存", "剩余", "还有", "存放", "在哪些库", "库位")) {
            return "data_query";
        }
        if (containsAny(value, "托盘", "二维码")) {
            return "data_query";
        }
        if (containsAny(value, "化验", "检验", "质量", "不合格", "无标准")) {
            return "data_query";
        }
        return null;
    }

    private String detectBusinessDomain(String text) {
        String value = nullToEmpty(text);
        if (isWriteOperationText(value)) {
            return writeOperationDomain(value);
        }
        if (containsAny(value, "生产订单")) {
            return "production";
        }
        if (containsAny(value, "化验", "检验", "质量", "不合格", "无标准")) {
            return "assay";
        }
        if (containsAny(value, "托盘", "二维码")) {
            return "pallet";
        }
        if (containsAny(value, "库位", "仓库", "容量", "库存", "存放")) {
            return "inventory";
        }
        return null;
    }

    private String fallbackIntentSubtype(String intentType, String question) {
        String value = nullToEmpty(question);
        if ("write_operation".equals(intentType)) {
            return writeOperationDomain(value);
        }
        if ("ambiguous".equals(intentType)) {
            return "inventory_query_missing_scope";
        }
        if ("capability".equals(intentType)) {
            return containsAny(value, "你是什么", "你是谁") ? "assistant_identity" : "capability_scope";
        }
        if ("report_analysis".equals(intentType) || "unsupported".equals(intentType)) {
            return unsupportedCapabilitySubtype(value);
        }
        if ("data_query".equals(intentType) && containsAny(value, "库位", "有什么", "有啥")) {
            return "warehouse_inventory_contents";
        }
        return null;
    }

    private String fallbackSupportStatus(String intentType) {
        if ("write_operation".equals(intentType)) {
            return "unsupported";
        }
        if ("ambiguous".equals(intentType)) {
            return "ambiguous";
        }
        if ("report_analysis".equals(intentType) || "unsupported".equals(intentType)) {
            return "unsupported";
        }
        if ("capability".equals(intentType) || "smalltalk".equals(intentType) || "data_query".equals(intentType)) {
            return "supported";
        }
        return null;
    }

    private String fallbackNextAction(String intentType) {
        if ("write_operation".equals(intentType)) {
            return "explain_unsupported";
        }
        if ("ambiguous".equals(intentType)) {
            return "ask_clarification";
        }
        if ("report_analysis".equals(intentType) || "unsupported".equals(intentType)) {
            return "explain_unsupported";
        }
        if ("capability".equals(intentType) || "smalltalk".equals(intentType)) {
            return "answer_directly";
        }
        if ("data_query".equals(intentType)) {
            return "call_tool";
        }
        return null;
    }

    private boolean isWriteOperationText(String text) {
        if (containsAny(text, "导出", "报表")) {
            return false;
        }
        return containsAny(text, "帮我出库", "出库", "入库", "调拨", "移库", "改库存", "调整库存", "新增产品",
                "修改产品", "修改指标", "质量标准", "写入化验", "导入化验", "创建生产订单", "修改生产订单");
    }

    private String writeOperationDomain(String text) {
        if (text.contains("出库")) {
            return "outbound";
        }
        if (text.contains("入库")) {
            return "inbound";
        }
        if (text.contains("调拨") || text.contains("移库")) {
            return "transfer";
        }
        if (containsAny(text, "化验", "指标", "质量标准")) {
            return "assay_write";
        }
        if (text.contains("生产订单")) {
            return "production_order";
        }
        if (text.contains("产品")) {
            return "reference_data";
        }
        return "business_write";
    }

    private List<String> recognizedEntities(String text) {
        List<String> entities = new ArrayList<>();
        String value = nullToEmpty(text);
        if (containsAny(value, "这些", "它", "刚才", "这个", "该产品")) {
            entities.add("CONTEXT_REFERENCE");
        }
        if (value.matches(".*\\d+\\s*号?\\s*(库位|仓库|库).*")) {
            entities.add("WAREHOUSE_NAME_TEXT");
        }
        if (containsAny(value, "黄冰糖", "白冰糖", "冰糖", "砂糖")) {
            entities.add("PRODUCT_NAME_TEXT");
        }
        return entities;
    }

    private List<String> missingSlots(AgentMessageReview review, String text) {
        List<String> slots = new ArrayList<>();
        String answer = nullToEmpty(review.getAssistantAnswerTextSafe());
        if (containsAny(answer, "请先选择或输入一个明确产品", "请提供明确产品名称")) {
            slots.add("PRODUCT");
        }
        if (containsAny(answer, "请提供库位名称", "换一个库位名称")) {
            slots.add("WAREHOUSE");
        }
        if (containsAny(answer, "托盘码")) {
            slots.add("PALLET_CODE");
        }
        return slots;
    }

    private String detectAnswerType(AgentMessageReview review) {
        if ("NEEDS_REVIEW".equals(review.getAnswerStatus())) {
            return "needs_review";
        }
        if ("LOW_CONFIDENCE".equals(review.getAnswerStatus())) {
            return "low_confidence";
        }
        if (containsAny(review.getAssistantAnswerTextSafe(), "未接入", "还没有接入", "还没有对应", "没有对应只读分析工具")) {
            return "capability_unsupported";
        }
        if (containsAny(review.getAssistantAnswerTextSafe(), "未找到", "暂不支持", "无法准确")) {
            return "fallback_or_no_match";
        }
        return "business_answer";
    }

    private String joinToolNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        Set<String> safeNames = names.stream()
                .filter(StringUtils::hasText)
                .map(name -> limit(name.replaceAll("[^A-Za-z0-9_:-]", ""), 100))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (safeNames.isEmpty()) {
            return null;
        }
        return limit(String.join(",", safeNames), 500);
    }

    private String clean(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text
                .replaceAll("(?i)Authorization\\s*:\\s*Bearer\\s+[A-Za-z0-9._\\-]+", "[AUTH_REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "[AUTH_REDACTED]")
                .replaceAll("(?i)(delegation[_-]?token|refresh[_-]?token|token|api[_-]?key|password|secret)\\s*[:=]\\s*[^\\s,;]+", "[SECRET_REDACTED]")
                .replaceAll("(?i)(stack[_-]?trace|exception|error[_-]?stack)\\s*[:=]\\s*[^\\n]+", "[STACK_REDACTED]")
                .replaceAll("(?i)jdbc:[^\\s,;]+", "[DB_CONNECTION_REDACTED]")
                .replaceAll("(?m)^\\s*at\\s+.+$", "[STACK_REDACTED]")
                .replaceAll("(?i)chain[-_ ]?of[-_ ]?thought", "[REDACTED]")
                .trim();
    }

    private boolean containsAny(String text, String... keywords) {
        String value = nullToEmpty(text);
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value, String fallback, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return limit(value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_"), maxLength);
    }

    private String normalizeLower(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private boolean isSmallTalkOrCapability(AgentMessageReviewRecordDTO dto) {
        if (dto == null) {
            return false;
        }
        String intentType = normalizeLower(dto.getIntentType());
        return "smalltalk".equals(intentType)
                || "capability".equals(intentType)
                || containsAny(nullToEmpty(dto.getUserQuestion()), "你是什么", "你是谁", "能做什么", "介绍一下");
    }

    private boolean isUnsupportedCapability(AgentMessageReviewRecordDTO dto, String question, String answer) {
        if (dto == null) {
            return false;
        }
        String intentType = normalizeLower(dto.getIntentType());
        if ("write_operation".equals(intentType)) {
            return false;
        }
        String supportStatus = normalizeLower(dto.getSupportStatus());
        String nextAction = normalizeLower(dto.getNextAction());
        boolean unsupportedRoute = "unsupported".equals(intentType)
                || "report_analysis".equals(intentType)
                || "unsupported".equals(supportStatus)
                || "explain_unsupported".equals(nextAction);
        boolean unsupportedAnswer = containsAny(answer,
                "未接入",
                "还没有接入",
                "还没有对应",
                "当前还没有",
                "不能直接汇总",
                "不能直接生成",
                "没有对应只读分析工具",
                "没有接入安全的报表",
                "工具还没接入");
        return unsupportedRoute && (unsupportedAnswer || isUnsupportedCapabilityQuestion(question));
    }

    private boolean isUnsupportedCapabilityQuestion(String text) {
        String value = nullToEmpty(text);
        boolean assayAnalysis = containsAny(value, "化验异常", "化验趋势", "哪些产品化验", "化验不合格")
                || (containsAny(value, "化验", "质检", "质量")
                && containsAny(value, "最近", "近", "异常", "趋势", "哪些产品", "不合格", "汇总", "统计", "分析"));
        return assayAnalysis || containsAny(value, "报表", "导出", "生产订单");
    }

    private String unsupportedCapabilitySubtype(String text) {
        String value = nullToEmpty(text);
        if (containsAny(value, "生产订单")) {
            return "production_order_tool_missing";
        }
        if (containsAny(value, "报表", "导出")) {
            return "report_export_tool_missing";
        }
        if (isUnsupportedCapabilityQuestion(value)) {
            return "assay_analysis_tool_missing";
        }
        return "business_tool_missing";
    }

    private String shortText(String text, int maxLength) {
        String safe = clean(text);
        if (!StringUtils.hasText(safe)) {
            return "未记录";
        }
        return limit(safe.replaceAll("\\s+", " "), maxLength);
    }

    private String nullToUnknown(String value) {
        return StringUtils.hasText(value) ? value : "UNKNOWN";
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String matchPrefix(AuditEvidenceContext evidenceContext) {
        return evidenceContext.weakMatch() ? "按时间窗口弱关联；" : "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static class AuditEvidenceContext {
        private final List<AgentToolAuditLog> auditLogs;
        private final List<AgentInterruptState> interrupts;
        private final boolean weakMatch;

        private AuditEvidenceContext(List<AgentToolAuditLog> auditLogs,
                                     List<AgentInterruptState> interrupts,
                                     boolean weakMatch) {
            this.auditLogs = auditLogs;
            this.interrupts = interrupts;
            this.weakMatch = weakMatch;
        }

        private static AuditEvidenceContext empty() {
            return new AuditEvidenceContext(List.of(), List.of(), false);
        }

        private List<AgentToolAuditLog> runtimeAudits() {
            return auditLogs.stream()
                    .filter(item -> "agent_runtime".equals(item.getToolName()))
                    .toList();
        }

        private List<AgentToolAuditLog> businessToolAudits() {
            return auditLogs.stream()
                    .filter(item -> !"agent_runtime".equals(item.getToolName()))
                    .toList();
        }

        private List<AgentInterruptState> interrupts() {
            return interrupts;
        }

        private boolean weakMatch() {
            return weakMatch;
        }

        private boolean hasNonEmptyMainToolResult() {
            return businessToolAudits().stream()
                    .filter(item -> isMainToolName(item.getToolName()))
                    .filter(item -> "SUCCESS".equalsIgnoreCase(item.getResultCode()))
                    .anyMatch(item -> responseSummaryLooksNonEmpty(item.getResponseSummary()));
        }

        private boolean responseSummaryLooksNonEmpty(String summary) {
            String value = summary == null ? "" : summary;
            return value.matches(".*(candidateCount|groupCount|warehouseCount|productCount|palletCount|locationCount)\"?\\s*[:=]\\s*[1-9][0-9]*.*")
                    || value.matches(".*(totalEquivalentPieces|remainingCapacity)\"?\\s*[:=]\\s*[1-9][0-9]*.*")
                    || value.contains("empty\":false")
                    || value.matches(".*totalStockText\"?\\s*[:=]\\s*\"?(?!0板0件)[^\";，。]+.*");
        }

        private static boolean isMainToolName(String toolName) {
            return toolName != null
                    && !"agent_runtime".equals(toolName)
                    && !"resolve_products".equals(toolName)
                    && !"resolve_warehouses".equals(toolName);
        }
    }
}
