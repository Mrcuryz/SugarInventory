package com.Laibin.SugarInventory.agent.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.domain.po.AgentMessageReview;
import com.Laibin.SugarInventory.domain.po.AgentMessageReviewEvidence;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewEvidenceMapper;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentMessageReviewServiceImpl implements AgentMessageReviewService {
    private static final int SAFE_TEXT_LIMIT = 10000;

    private final AgentMessageReviewMapper reviewMapper;
    private final AgentMessageReviewEvidenceMapper evidenceMapper;
    private final AgentSessionService agentSessionService;

    public AgentMessageReviewServiceImpl(AgentMessageReviewMapper reviewMapper,
                                         AgentMessageReviewEvidenceMapper evidenceMapper,
                                         AgentSessionService agentSessionService) {
        this.reviewMapper = reviewMapper;
        this.evidenceMapper = evidenceMapper;
        this.agentSessionService = agentSessionService;
    }

    @Override
    public AgentMessageReview recordAssistantTurn(LoginUser loginUser, String agentSessionId, AgentMessageReviewRecordDTO dto) {
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        AgentMessageReview review = findExisting(agentSessionId, dto.getMessageId());
        boolean exists = review != null;
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
        review.setActualToolNames(joinToolNames(dto.getActualToolNames()));
        review.setActualIntentSummary(limit(dto.getActualIntentSummary(), 1000));
        applyAutomaticClassification(review, dto);
        review.setUpdatedAt(LocalDateTime.now());

        if (exists) {
            reviewMapper.updateById(review);
        } else {
            reviewMapper.insert(review);
        }
        insertEvidence(review.getId(), "AUTO_HEURISTIC", review.getMessageId(), buildAutoEvidence(review, dto));
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
        review.setUpdatedAt(LocalDateTime.now());

        if (exists) {
            reviewMapper.updateById(review);
        } else {
            reviewMapper.insert(review);
        }
        insertEvidence(review.getId(), "USER_FEEDBACK", messageId, buildFeedbackEvidence(dto));
        return review;
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

    private void applyAutomaticClassification(AgentMessageReview review, AgentMessageReviewRecordDTO dto) {
        String providedStatus = normalize(dto.getAnswerStatus(), null, 40);
        String providedConfidence = normalize(dto.getConfidenceLevel(), null, 20);
        String question = nullToEmpty(dto.getUserQuestion());
        String answer = nullToEmpty(dto.getAssistantAnswerTextSafe());
        boolean businessQuestion = containsAny(question, "库存", "库位", "托盘", "化验", "不合格", "无标准", "二维码");
        boolean noTool = dto.getActualToolNames() == null || dto.getActualToolNames().isEmpty();
        boolean fallback = containsAny(answer, "未找到匹配", "暂不支持", "无法准确", "请换一个更准确", "没有执行", "没有查询");

        if ("cancelled".equalsIgnoreCase(dto.getFinishReason())) {
            review.setAnswerStatus("CANCELLED");
            review.setConfidenceLevel("UNKNOWN");
            review.setFailureDomain("SYSTEM");
            review.setFailureCategory("CLIENT_CANCELLED");
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
        if (businessQuestion && noTool) {
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
                review.setFailureDomain("TOOL");
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

    private String buildAutoEvidence(AgentMessageReview review, AgentMessageReviewRecordDTO dto) {
        String tools = joinToolNames(dto.getActualToolNames());
        if (!StringUtils.hasText(tools)) {
            return "本轮未记录实际工具调用；answerStatus=" + review.getAnswerStatus();
        }
        return "本轮实际调用工具：" + tools + "；answerStatus=" + review.getAnswerStatus();
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
                .replaceAll("(?i)Authorization\\s*:\\s*Bearer\\s+[A-Za-z0-9._\\-]+", "Authorization: [REDACTED]")
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._\\-]+", "Bearer [REDACTED]")
                .replaceAll("(?i)token\\s*[:=]\\s*[A-Za-z0-9._\\-]+", "token=[REDACTED]")
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

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
