package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.model.FinishInboundExecutionPreviewSnapshot;
import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionPreviewRefCodec;
import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionPermissionPolicy;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPreviewVO;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
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
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionPreviewArchiveService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern PREVIEW_REF = Pattern.compile("^fip1_[A-Za-z0-9_-]{43}$");
    private final AgentFinishInboundExecutionPreviewMapper previewMapper;
    private final FinishInboundExecutionPreviewRefCodec refCodec;
    private final ObjectMapper objectMapper;

    @Transactional
    public FinishInboundExecutionPreviewVO persistReady(
            FinishInboundExecutionPreviewVO preview,
            Integer ownerUserId,
            String agentSessionId,
            Set<String> currentAuthorities) {
        if (preview == null || !"READY".equals(preview.getPreviewStatus())
                || !preview.isReadyForUserConfirmation()) {
            return preview;
        }
        validateReadyPreview(preview, ownerUserId, currentAuthorities);
        FinishInboundExecutionPreviewSnapshot snapshot = preview.getServerSnapshot();
        List<String> entityRefs = preview.getItems().stream()
                .map(FinishInboundExecutionPreviewVO.Item::getPalletCode)
                .map(this::normalizePalletCode)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (entityRefs.isEmpty() || entityRefs.size() != preview.getItems().size()) {
            throw new BusinessException(409, "成品入库执行预览实体范围无效，请重新预览");
        }

        String normalizedInputJson = writeJson(snapshot.getItems().stream()
                .map(FinishInboundExecutionPreviewSnapshot.Item::getNormalizedInput)
                .toList(), "保存成品入库规范化输入失败");
        String entityStateJson = writeJson(snapshot, "保存成品入库状态快照失败");
        String stateDigest = sha256(entityStateJson);
        String previewRef = refCodec.encode(ownerUserId, stateDigest, preview.getExpiresAt());
        preview.setStateDigest(stateDigest);
        preview.setPreviewRef(previewRef);
        String publicPayloadJson = writeJson(preview, "保存成品入库公开预览失败");
        String entityRefsJson = writeJson(entityRefs, "保存成品入库预览实体失败");

        AgentFinishInboundExecutionPreview stored = new AgentFinishInboundExecutionPreview();
        stored.setPreviewRef(previewRef);
        stored.setOwnerUserId(ownerUserId);
        stored.setAgentSessionId(requireSessionId(agentSessionId));
        stored.setPreviewVersion(preview.getPreviewVersion());
        stored.setStatus("READY");
        stored.setRequiredPermissions(
                FinishInboundExecutionPermissionPolicy.selectPreviewSnapshot(currentAuthorities));
        stored.setEntityRefsJson(entityRefsJson);
        stored.setNormalizedInputJson(normalizedInputJson);
        stored.setEntityStateJson(entityStateJson);
        stored.setPublicPayloadJson(publicPayloadJson);
        stored.setStateDigest(stateDigest);
        stored.setNormalizedRequestSha256(sha256(normalizedInputJson));
        stored.setContentSha256(sha256(publicPayloadJson + "\n" + entityStateJson));
        stored.setPreviewedAt(preview.getPreviewedAt());
        stored.setExpiresAt(preview.getExpiresAt());
        stored.setCreatedAt(LocalDateTime.now(BUSINESS_ZONE));
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
        AgentFinishInboundExecutionPreview stored = previewMapper.selectOne(
                new LambdaQueryWrapper<AgentFinishInboundExecutionPreview>()
                        .eq(AgentFinishInboundExecutionPreview::getPreviewRef, normalizedRef)
                        .last("LIMIT 1"));
        if (stored == null || !ownerUserId.equals(stored.getOwnerUserId())) {
            throw new BusinessException(404, "成品入库执行预览不存在、已过期或不属于当前用户");
        }
        verifySession(stored.getAgentSessionId(), requireSessionId(agentSessionId));
        if (!"READY".equals(stored.getStatus())) {
            throw new BusinessException(409, "成品入库执行预览已失效，请重新预览");
        }
        FinishInboundExecutionPermissionPolicy.requireStoredPreviewSnapshot(
                stored.getRequiredPermissions(), currentAuthorities);
        if (stored.getExpiresAt() == null
                || !stored.getExpiresAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new BusinessException(410, "成品入库执行预览已过期，请重新预览");
        }
        verifyStoredContent(stored);
        JsonNode publicPayload = readJson(stored.getPublicPayloadJson(), "读取成品入库公开预览失败");
        JsonNode entityState = readJson(stored.getEntityStateJson(), "读取成品入库状态快照失败");
        if (!normalizedRef.equals(publicPayload.path("previewRef").asText())
                || !Objects.equals(stored.getStateDigest(), publicPayload.path("stateDigest").asText())
                || !Objects.equals(stored.getPreviewVersion(), publicPayload.path("previewVersion").asInt(-1))
                || !"READY".equals(publicPayload.path("previewStatus").asText())
                || !sha256(stored.getEntityStateJson()).equalsIgnoreCase(stored.getStateDigest())
                || !sha256(stored.getNormalizedInputJson()).equalsIgnoreCase(stored.getNormalizedRequestSha256())) {
            throw new BusinessException(409, "成品入库执行预览快照校验失败，请重新预览");
        }
        return new StoredPreview(stored, publicPayload, entityState);
    }

    @Transactional(readOnly = true)
    public StoredPreview loadLatestOwnedActiveByPalletCodes(
            List<String> palletCodes,
            Integer ownerUserId,
            String agentSessionId,
            Set<String> currentAuthorities) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
        String sessionId = requireSessionId(agentSessionId);
        FinishInboundExecutionPermissionPolicy.selectPreviewSnapshot(currentAuthorities);
        TreeSet<String> requested = new TreeSet<>();
        if (palletCodes != null) {
            palletCodes.stream().map(this::normalizePalletCode).forEach(requested::add);
        }
        if (requested.isEmpty() || requested.size() > 20) {
            throw new BusinessException(400, "待确认成品入库托盘范围无效");
        }
        List<AgentFinishInboundExecutionPreview> candidates = previewMapper.selectList(
                new LambdaQueryWrapper<AgentFinishInboundExecutionPreview>()
                        .eq(AgentFinishInboundExecutionPreview::getOwnerUserId, ownerUserId)
                        .eq(AgentFinishInboundExecutionPreview::getAgentSessionId, sessionId)
                        .eq(AgentFinishInboundExecutionPreview::getStatus, "READY")
                        .gt(AgentFinishInboundExecutionPreview::getExpiresAt,
                                LocalDateTime.now(BUSINESS_ZONE))
                        .orderByDesc(AgentFinishInboundExecutionPreview::getId)
                        .last("LIMIT 20"));
        for (AgentFinishInboundExecutionPreview candidate : candidates) {
            List<String> storedCodes = readStringList(candidate.getEntityRefsJson());
            if (requested.equals(new TreeSet<>(storedCodes))) {
                return loadOwnedActive(candidate.getPreviewRef(), ownerUserId, sessionId,
                        currentAuthorities);
            }
        }
        throw new BusinessException(404, "没有找到与当前卡片一致且仍有效的成品入库预览，请重新生成预览");
    }

    private void validateReadyPreview(
            FinishInboundExecutionPreviewVO preview,
            Integer ownerUserId,
            Set<String> currentAuthorities) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
        if (preview.getPreviewVersion() != 1 || preview.getServerSnapshot() == null) {
            throw new BusinessException(409, "成品入库执行预览协议或服务端快照无效");
        }
        if (preview.getPreviewedAt() == null || preview.getExpiresAt() == null
                || !preview.getExpiresAt().isAfter(preview.getPreviewedAt())) {
            throw new BusinessException(409, "成品入库执行预览有效期无效，请重新预览");
        }
        if (preview.getItems() == null || preview.getItems().isEmpty()
                || preview.getItems().size() > 20
                || preview.getItems().size() != preview.getRequestedItemCount()) {
            throw new BusinessException(409, "成品入库执行预览范围无效，请重新预览");
        }
        FinishInboundExecutionPermissionPolicy.selectPreviewSnapshot(currentAuthorities);
    }

    private void verifyStoredContent(AgentFinishInboundExecutionPreview stored) {
        if (stored.getPublicPayloadJson() == null || stored.getEntityStateJson() == null
                || stored.getNormalizedInputJson() == null || stored.getContentSha256() == null
                || !sha256(stored.getPublicPayloadJson() + "\n" + stored.getEntityStateJson())
                .equalsIgnoreCase(stored.getContentSha256())) {
            throw new BusinessException(409, "成品入库执行预览内容校验失败，请重新预览");
        }
    }

    private JsonNode readJson(String value, String message) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private List<String> readStringList(String value) {
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取成品入库预览实体范围失败", exception);
        }
    }

    private String writeJson(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private static void verifySession(String storedSessionId, String currentSessionId) {
        if (!Objects.equals(storedSessionId, currentSessionId)) {
            throw new BusinessException(403, "成品入库执行预览不属于当前 Agent 会话");
        }
    }

    private static String requireSessionId(String value) {
        String normalized = normalizeSessionId(value);
        if (normalized == null) {
            throw new BusinessException(400, "Agent 会话引用不能为空");
        }
        return normalized;
    }

    private static String normalizePreviewRef(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!PREVIEW_REF.matcher(normalized).matches()) {
            throw new BusinessException(400, "成品入库执行预览引用无效");
        }
        return normalized;
    }

    private static String normalizeSessionId(String value) {
        if (value == null || value.isBlank()) return null;
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
            throw new BusinessException(409, "成品入库执行预览包含无效托盘引用，请重新预览");
        }
        return normalized;
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
            AgentFinishInboundExecutionPreview row,
            JsonNode publicPayload,
            JsonNode entityState) {
    }
}
