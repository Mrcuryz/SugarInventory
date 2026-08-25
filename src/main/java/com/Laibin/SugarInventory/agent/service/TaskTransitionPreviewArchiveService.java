package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionPermissionPolicy;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentTaskTransitionPreview;
import com.Laibin.SugarInventory.domain.vo.TaskTransitionPreviewVO;
import com.Laibin.SugarInventory.mapper.AgentTaskTransitionPreviewMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TaskTransitionPreviewArchiveService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern PREVIEW_REF = Pattern.compile("^tpr1_[A-Za-z0-9_-]{43}$");
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Set<String> SUPPORTED_TRANSITIONS = Set.of(
            "CONFIRM_FINISH_INBOUND",
            "CONFIRM_FINISH_OUTBOUND",
            "CONFIRM_TRANSFER");
    private static final Set<String> MANUAL_TRANSITION_PERMISSIONS =
            Set.of("task:view", "task:confirm");
    private static final String MANUAL_TRANSITION_PERMISSION_SNAPSHOT = "task:confirm,task:view";

    private final AgentTaskTransitionPreviewMapper previewMapper;
    private final ObjectMapper objectMapper;

    public void requireTransitionAccess(
            String transition, Set<String> currentAuthorities) {
        if (!SUPPORTED_TRANSITIONS.contains(transition)) {
            throw new BusinessException(400, "任务预览转换类型不受支持");
        }
        permissionSnapshotFor(transition, currentAuthorities);
    }

    @Transactional
    public TaskTransitionPreviewVO persistReady(
            TaskTransitionPreviewVO preview,
            Integer ownerUserId,
            String agentSessionId,
            Set<String> currentAuthorities) {
        if (preview == null || !"READY".equals(preview.getPreviewStatus())
                || !preview.isCanOpenBusinessDialog()) {
            return preview;
        }
        validateReadyPreview(preview, ownerUserId, currentAuthorities);

        List<String> entityRefs = preview.getTasks().stream()
                .map(TaskTransitionPreviewVO.Task::getPalletCode)
                .map(this::normalizePalletCode)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (entityRefs.isEmpty() || entityRefs.size() != preview.getTasks().size()) {
            throw new BusinessException(409, "任务预览实体引用不完整，请重新预览");
        }

        String payloadJson = writeJson(preview, "保存任务预览快照失败");
        String entityRefsJson = writeJson(entityRefs, "保存任务预览实体失败");
        String normalizedRequest = preview.getPreviewVersion()
                + "|" + preview.getTransition()
                + "|" + String.join(",", entityRefs);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);

        AgentTaskTransitionPreview stored = new AgentTaskTransitionPreview();
        stored.setPreviewRef(preview.getPreviewRef());
        stored.setOwnerUserId(ownerUserId);
        stored.setAgentSessionId(normalizeSessionId(agentSessionId));
        stored.setPreviewVersion(preview.getPreviewVersion());
        stored.setTransitionType(preview.getTransition());
        stored.setStatus("READY");
        stored.setRequiredPermissions(permissionSnapshotFor(
                preview.getTransition(), currentAuthorities));
        stored.setEntityRefsJson(entityRefsJson);
        stored.setPayloadJson(payloadJson);
        stored.setStateDigest(preview.getStateDigest());
        stored.setNormalizedRequestSha256(sha256(normalizedRequest));
        stored.setContentSha256(sha256(payloadJson));
        stored.setPreviewedAt(preview.getPreviewedAt());
        stored.setExpiresAt(preview.getExpiresAt());
        stored.setCreatedAt(now);
        previewMapper.insert(stored);
        return preview;
    }

    @Transactional(readOnly = true)
    public StoredPreview loadOwnedActive(
            String previewRef,
            Integer ownerUserId,
            String agentSessionId,
            Set<String> currentAuthorities) {
        String normalizedRef = normalizePreviewRef(previewRef);
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
        AgentTaskTransitionPreview stored = previewMapper.selectOne(
                new LambdaQueryWrapper<AgentTaskTransitionPreview>()
                        .eq(AgentTaskTransitionPreview::getPreviewRef, normalizedRef)
                        .last("LIMIT 1"));
        if (stored == null || !ownerUserId.equals(stored.getOwnerUserId())) {
            throw new BusinessException(404, "任务预览不存在、已过期或不属于当前用户");
        }
        verifySession(stored.getAgentSessionId(), agentSessionId);
        if (!"READY".equals(stored.getStatus())) {
            throw new BusinessException(409, "任务预览已失效，请重新预览");
        }
        requireStoredPermissionSnapshot(stored, currentAuthorities);
        if (stored.getExpiresAt() == null
                || !stored.getExpiresAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new BusinessException(410, "任务预览已过期，请重新预览");
        }
        JsonNode snapshot = parseVerifiedPayload(stored);
        List<String> snapshotEntityRefs = entityRefsFromSnapshot(snapshot);
        String normalizedRequest = snapshot.path("previewVersion").asInt(-1)
                + "|" + snapshot.path("transition").asText()
                + "|" + String.join(",", snapshotEntityRefs);
        if (!normalizedRef.equals(snapshot.path("previewRef").asText())
                || !Objects.equals(stored.getStateDigest(), snapshot.path("stateDigest").asText())
                || !Objects.equals(stored.getTransitionType(), snapshot.path("transition").asText())
                || !Objects.equals(stored.getPreviewVersion(), snapshot.path("previewVersion").asInt(-1))
                || !sha256(normalizedRequest).equalsIgnoreCase(
                        stored.getNormalizedRequestSha256() == null ? "" : stored.getNormalizedRequestSha256())
                || !entityRefsJsonMatches(stored.getEntityRefsJson(), snapshotEntityRefs)) {
            throw new BusinessException(409, "任务预览快照校验失败，请重新预览");
        }
        return new StoredPreview(stored, snapshot);
    }

    private void validateReadyPreview(
            TaskTransitionPreviewVO preview,
            Integer ownerUserId,
            Set<String> currentAuthorities) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
        normalizePreviewRef(preview.getPreviewRef());
        if (preview.getPreviewVersion() != 1
                || !SUPPORTED_TRANSITIONS.contains(preview.getTransition())) {
            throw new BusinessException(400, "任务预览协议或转换类型不受支持");
        }
        if (preview.getStateDigest() == null
                || !SHA256.matcher(preview.getStateDigest()).matches()) {
            throw new BusinessException(409, "任务预览状态摘要无效，请重新预览");
        }
        if (preview.getPreviewedAt() == null || preview.getExpiresAt() == null
                || !preview.getExpiresAt().isAfter(preview.getPreviewedAt())) {
            throw new BusinessException(409, "任务预览有效期无效，请重新预览");
        }
        if (preview.getTasks() == null || preview.getTasks().isEmpty()
                || preview.getTasks().size() > 20) {
            throw new BusinessException(409, "任务预览实体范围无效，请重新预览");
        }
        permissionSnapshotFor(preview.getTransition(), currentAuthorities);
    }

    private JsonNode parseVerifiedPayload(AgentTaskTransitionPreview stored) {
        String payload = stored.getPayloadJson();
        if (payload == null || stored.getContentSha256() == null
                || !sha256(payload).equalsIgnoreCase(stored.getContentSha256())) {
            throw new BusinessException(409, "任务预览内容校验失败，请重新预览");
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取任务预览快照失败", exception);
        }
    }

    private List<String> entityRefsFromSnapshot(JsonNode snapshot) {
        JsonNode tasks = snapshot.path("tasks");
        if (!tasks.isArray() || tasks.isEmpty() || tasks.size() > 20) {
            throw new BusinessException(409, "任务预览实体快照校验失败，请重新预览");
        }
        return java.util.stream.StreamSupport.stream(tasks.spliterator(), false)
                .map(task -> normalizePalletCode(task.path("palletCode").asText()))
                .distinct()
                .sorted()
                .toList();
    }

    private boolean entityRefsJsonMatches(String entityRefsJson, List<String> snapshotEntityRefs) {
        if (entityRefsJson == null) {
            return false;
        }
        try {
            JsonNode storedRefs = objectMapper.readTree(entityRefsJson);
            if (!storedRefs.isArray() || storedRefs.size() != snapshotEntityRefs.size()) {
                return false;
            }
            List<String> normalizedStoredRefs = java.util.stream.StreamSupport
                    .stream(storedRefs.spliterator(), false)
                    .map(JsonNode::asText)
                    .map(this::normalizePalletCode)
                    .distinct()
                    .sorted()
                    .toList();
            return normalizedStoredRefs.equals(snapshotEntityRefs);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private static String permissionSnapshotFor(
            String transition, Set<String> currentAuthorities) {
        if ("CONFIRM_FINISH_INBOUND".equals(transition)) {
            return FinishInboundExecutionPermissionPolicy.selectPreviewSnapshot(currentAuthorities);
        }
        Set<String> authorities = currentAuthorities == null ? Set.of() : currentAuthorities;
        if (!authorities.containsAll(MANUAL_TRANSITION_PERMISSIONS)) {
            throw new BusinessException(403, "当前用户没有查看或继续此任务预览的权限");
        }
        return MANUAL_TRANSITION_PERMISSION_SNAPSHOT;
    }

    private static void requireStoredPermissionSnapshot(
            AgentTaskTransitionPreview stored, Set<String> currentAuthorities) {
        if (!SUPPORTED_TRANSITIONS.contains(stored.getTransitionType())) {
            throw new BusinessException(409, "任务预览权限快照校验失败，请重新预览");
        }
        if ("CONFIRM_FINISH_INBOUND".equals(stored.getTransitionType())) {
            FinishInboundExecutionPermissionPolicy.requireStoredPreviewSnapshot(
                    stored.getRequiredPermissions(), currentAuthorities);
            return;
        }
        if (!MANUAL_TRANSITION_PERMISSION_SNAPSHOT.equals(stored.getRequiredPermissions())) {
            throw new BusinessException(409, "任务预览权限快照校验失败，请重新预览");
        }
        permissionSnapshotFor(stored.getTransitionType(), currentAuthorities);
    }

    private static void verifySession(String storedSessionId, String currentSessionId) {
        if (storedSessionId == null) {
            if (currentSessionId != null && !currentSessionId.isBlank()) {
                throw new BusinessException(403, "任务预览不属于当前 Agent 会话");
            }
            return;
        }
        if (!storedSessionId.equals(normalizeSessionId(currentSessionId))) {
            throw new BusinessException(403, "任务预览不属于当前 Agent 会话");
        }
    }

    private static String normalizePreviewRef(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!PREVIEW_REF.matcher(normalized).matches()) {
            throw new BusinessException(400, "任务预览引用无效");
        }
        return normalized;
    }

    private static String normalizeSessionId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new BusinessException(400, "Agent 会话引用无效");
        }
        return normalized;
    }

    private String normalizePalletCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 100
                || !normalized.matches("[A-Z0-9-]+")) {
            throw new BusinessException(409, "任务预览包含无效托盘引用，请重新预览");
        }
        return normalized;
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    public record StoredPreview(
            AgentTaskTransitionPreview row,
            JsonNode snapshot) {
    }
}
