package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionControlCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionConfirmationVO;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionConfirmationMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionConfirmationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String S2_REQUIRED_PERMISSIONS = "task:confirm,task:view";
    private static final Set<String> S2_REQUIRED_PERMISSION_SET = Set.of("task:view", "task:confirm");
    private static final String S3_REQUIRED_PERMISSIONS =
            "agent:finish-inbound:execute,task:confirm,task:view";
    private static final Set<String> S3_REQUIRED_PERMISSION_SET = Set.of(
            "agent:finish-inbound:execute", "task:view", "task:confirm");

    private final FinishInboundExecutionPreviewArchiveService previewArchiveService;
    private final FinishInboundExecutionStateVerifier stateVerifier;
    private final FinishInboundExecutionControlCodec codec;
    private final FinishInboundExecutionAuditService auditService;
    private final AgentFinishInboundExecutionPreviewMapper previewMapper;
    private final AgentFinishInboundExecutionConfirmationMapper confirmationMapper;

    @Value("${agent.finish-inbound.confirmation-ttl-seconds:120}")
    private long confirmationTtlSeconds;

    @Transactional(noRollbackFor = BusinessException.class)
    public FinishInboundExecutionConfirmationVO confirm(
            String previewRef, User user, String agentSessionId, Set<String> authorities) {
        return confirmInternal(previewRef, user, agentSessionId, authorities,
                S2_REQUIRED_PERMISSION_SET, S2_REQUIRED_PERMISSIONS, false);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public FinishInboundExecutionConfirmationVO confirmForDomainExecution(
            String previewRef, User user, String agentSessionId, Set<String> authorities) {
        return confirmInternal(previewRef, user, agentSessionId, authorities,
                S3_REQUIRED_PERMISSION_SET, S3_REQUIRED_PERMISSIONS, true);
    }

    private FinishInboundExecutionConfirmationVO confirmInternal(
            String previewRef, User user, String agentSessionId, Set<String> authorities,
            Set<String> requiredPermissionSet, String requiredPermissions,
            boolean allowConsumedReplay) {
        requireActor(user, agentSessionId, authorities, requiredPermissionSet);
        AgentFinishInboundExecutionConfirmation existing =
                confirmationMapper.selectByPreviewRefForUpdate(previewRef);
        if (existing != null) {
            return replayExisting(existing, user, agentSessionId, authorities,
                    requiredPermissionSet, requiredPermissions, allowConsumedReplay);
        }

        FinishInboundExecutionPreviewArchiveService.StoredPreview active =
                previewArchiveService.loadOwnedActive(
                        previewRef, user.getId(), agentSessionId, authorities);
        AgentFinishInboundExecutionPreview preview = previewMapper.selectByRefForUpdate(previewRef);
        if (preview == null || !Objects.equals(preview.getId(), active.row().getId())
                || !"READY".equals(preview.getStatus())) {
            throw new BusinessException(409, "成品入库执行预览已失效，请重新预览");
        }
        existing = confirmationMapper.selectByPreviewRefForUpdate(previewRef);
        if (existing != null) {
            return replayExisting(existing, user, agentSessionId, authorities,
                    requiredPermissionSet, requiredPermissions, allowConsumedReplay);
        }
        stateVerifier.verifyUnchanged(preview, user);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime tokenExpiry = now.plusSeconds(Math.max(30, confirmationTtlSeconds));
        if (preview.getExpiresAt().isBefore(tokenExpiry)) tokenExpiry = preview.getExpiresAt();
        if (!tokenExpiry.isAfter(now)) {
            throw new BusinessException(410, "成品入库执行预览已过期，请重新预览");
        }

        AgentFinishInboundExecutionConfirmation confirmation =
                new AgentFinishInboundExecutionConfirmation();
        confirmation.setConfirmationRef(codec.newConfirmationRef());
        confirmation.setPreviewId(preview.getId());
        confirmation.setPreviewRef(preview.getPreviewRef());
        confirmation.setOwnerUserId(user.getId());
        confirmation.setAgentSessionId(agentSessionId);
        confirmation.setStatus("CONFIRMED");
        confirmation.setRequiredPermissions(requiredPermissions);
        confirmation.setPreviewContentSha256(preview.getContentSha256());
        confirmation.setPreviewStateDigest(preview.getStateDigest());
        confirmation.setConfirmedAt(now);
        confirmation.setExpiresAt(tokenExpiry);
        confirmation.setCreatedAt(now);
        confirmation.setUpdatedAt(now);
        FinishInboundExecutionControlCodec.Credentials credentials = codec.credentials(confirmation);
        confirmation.setTokenSha256(FinishInboundExecutionControlCodec.sha256(credentials.executionToken()));
        confirmation.setIdempotencyKeySha256(
                FinishInboundExecutionControlCodec.sha256(credentials.idempotencyKey()));
        confirmationMapper.insert(confirmation);
        auditService.append(confirmation.getConfirmationRef(), null, user.getId(), agentSessionId,
                "USER_CONFIRMED", "READY", "CONFIRMED", "CONFIRMED", null,
                confirmation.getConfirmationRef() + "|" + preview.getStateDigest());
        preview.setStatus("CONFIRMED");
        previewMapper.updateById(preview);
        return toVo(confirmation, credentials, false);
    }

    @Transactional
    public FinishInboundExecutionConfirmationVO revoke(
            String confirmationRef, User user, String agentSessionId, Set<String> authorities) {
        return revokeInternal(confirmationRef, user, agentSessionId, authorities,
                S2_REQUIRED_PERMISSION_SET, S2_REQUIRED_PERMISSIONS);
    }

    @Transactional
    public FinishInboundExecutionConfirmationVO revokeDomainExecution(
            String confirmationRef, User user, String agentSessionId, Set<String> authorities) {
        return revokeInternal(confirmationRef, user, agentSessionId, authorities,
                S3_REQUIRED_PERMISSION_SET, S3_REQUIRED_PERMISSIONS);
    }

    private FinishInboundExecutionConfirmationVO revokeInternal(
            String confirmationRef, User user, String agentSessionId, Set<String> authorities,
            Set<String> requiredPermissionSet, String requiredPermissions) {
        requireActor(user, agentSessionId, authorities, requiredPermissionSet);
        AgentFinishInboundExecutionConfirmation confirmation =
                confirmationMapper.selectByRefForUpdate(confirmationRef);
        requireOwned(confirmation, user.getId(), agentSessionId, requiredPermissions);
        if ("REVOKED".equals(confirmation.getStatus())) {
            return toStatusVo(confirmation, "已撤销", true);
        }
        if ("CONSUMED".equals(confirmation.getStatus())) {
            throw new BusinessException(409, "本次确认已经使用，不能撤销");
        }
        String from = confirmation.getStatus();
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        confirmation.setStatus("REVOKED");
        confirmation.setRevokedAt(now);
        confirmation.setUpdatedAt(now);
        confirmationMapper.updateById(confirmation);
        AgentFinishInboundExecutionPreview preview = previewMapper.selectById(confirmation.getPreviewId());
        if (preview != null && !"CONSUMED".equals(preview.getStatus())) {
            preview.setStatus("REVOKED");
            preview.setRevokedAt(now);
            previewMapper.updateById(preview);
        }
        auditService.append(confirmationRef, null, user.getId(), agentSessionId,
                "USER_REVOKED", from, "REVOKED", "REVOKED", null, confirmationRef);
        return toStatusVo(confirmation, "已撤销", false);
    }

    private FinishInboundExecutionConfirmationVO replayExisting(
            AgentFinishInboundExecutionConfirmation confirmation,
            User user, String agentSessionId, Set<String> authorities,
            Set<String> requiredPermissionSet, String requiredPermissions,
            boolean allowConsumedReplay) {
        requireActor(user, agentSessionId, authorities, requiredPermissionSet);
        requireOwned(confirmation, user.getId(), agentSessionId, requiredPermissions);
        boolean consumedReplay = allowConsumedReplay && "CONSUMED".equals(confirmation.getStatus());
        if (!"CONFIRMED".equals(confirmation.getStatus()) && !consumedReplay) {
            throw new BusinessException(409, "本次确认已失效，请重新预览");
        }
        if (!consumedReplay && isExpired(confirmation)) {
            expire(confirmation);
            throw new BusinessException(410, "本次确认已过期，请重新预览");
        }
        if (!consumedReplay) {
            AgentFinishInboundExecutionPreview preview =
                    previewMapper.selectById(confirmation.getPreviewId());
            try {
                stateVerifier.verifyUnchanged(preview, user);
            } catch (BusinessException exception) {
                invalidate(confirmation, preview);
                throw exception;
            }
        }
        FinishInboundExecutionControlCodec.Credentials credentials = codec.credentials(confirmation);
        if (!codec.matchesHash(credentials.executionToken(), confirmation.getTokenSha256())
                || !codec.matchesHash(credentials.idempotencyKey(), confirmation.getIdempotencyKeySha256())) {
            throw new BusinessException(409, "确认凭据校验失败，请重新预览");
        }
        return toVo(confirmation, credentials, true,
                consumedReplay ? "已完成，可安全读取原结果" : "已确认");
    }

    private void expire(AgentFinishInboundExecutionConfirmation confirmation) {
        String from = confirmation.getStatus();
        confirmation.setStatus("EXPIRED");
        confirmation.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        confirmationMapper.updateById(confirmation);
        markPreview(confirmation.getPreviewId(), "EXPIRED");
        auditService.append(confirmation.getConfirmationRef(), null, confirmation.getOwnerUserId(),
                confirmation.getAgentSessionId(), "CONFIRMATION_EXPIRED", from, "EXPIRED",
                null, "CONFIRMATION_EXPIRED", confirmation.getConfirmationRef());
    }

    private void invalidate(AgentFinishInboundExecutionConfirmation confirmation,
                            AgentFinishInboundExecutionPreview preview) {
        String from = confirmation.getStatus();
        confirmation.setStatus("INVALIDATED");
        confirmation.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        confirmationMapper.updateById(confirmation);
        if (preview != null) {
            preview.setStatus("INVALIDATED");
            previewMapper.updateById(preview);
        }
        auditService.append(confirmation.getConfirmationRef(), null, confirmation.getOwnerUserId(),
                confirmation.getAgentSessionId(), "STATE_INVALIDATED", from, "INVALIDATED",
                null, "STATE_CHANGED", confirmation.getConfirmationRef());
    }

    private void markPreview(Long previewId, String status) {
        AgentFinishInboundExecutionPreview preview = previewMapper.selectById(previewId);
        if (preview != null) {
            preview.setStatus(status);
            previewMapper.updateById(preview);
        }
    }

    private static void requireActor(
            User user, String sessionId, Set<String> authorities, Set<String> requiredPermissions) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(400, "Agent 会话引用不能为空");
        }
        if (authorities == null || !authorities.containsAll(requiredPermissions)) {
            throw new BusinessException(403, "当前用户没有确认成品入库执行的权限");
        }
    }

    private static void requireOwned(AgentFinishInboundExecutionConfirmation confirmation,
                                     Integer userId, String agentSessionId,
                                     String requiredPermissions) {
        if (confirmation == null || !Objects.equals(confirmation.getOwnerUserId(), userId)) {
            throw new BusinessException(404, "成品入库执行确认不存在或不属于当前用户");
        }
        if (!Objects.equals(confirmation.getAgentSessionId(), agentSessionId)) {
            throw new BusinessException(403, "成品入库执行确认不属于当前 Agent 会话");
        }
        if (!requiredPermissions.equals(confirmation.getRequiredPermissions())) {
            throw new BusinessException(409, "成品入库执行确认权限快照校验失败");
        }
    }

    private static boolean isExpired(AgentFinishInboundExecutionConfirmation confirmation) {
        return confirmation.getExpiresAt() == null
                || !confirmation.getExpiresAt().isAfter(LocalDateTime.now(BUSINESS_ZONE));
    }

    private static FinishInboundExecutionConfirmationVO toVo(
            AgentFinishInboundExecutionConfirmation confirmation,
            FinishInboundExecutionControlCodec.Credentials credentials, boolean replayed) {
        return toVo(confirmation, credentials, replayed, "已确认");
    }

    private static FinishInboundExecutionConfirmationVO toVo(
            AgentFinishInboundExecutionConfirmation confirmation,
            FinishInboundExecutionControlCodec.Credentials credentials, boolean replayed,
            String statusLabel) {
        return FinishInboundExecutionConfirmationVO.builder()
                .confirmationRef(confirmation.getConfirmationRef())
                .confirmationStatus(confirmation.getStatus())
                .confirmationStatusLabel(statusLabel)
                .expiresAt(confirmation.getExpiresAt())
                .executionToken(credentials.executionToken())
                .idempotencyKey(credentials.idempotencyKey())
                .replayed(replayed)
                .build();
    }

    private static FinishInboundExecutionConfirmationVO toStatusVo(
            AgentFinishInboundExecutionConfirmation confirmation, String label, boolean replayed) {
        return FinishInboundExecutionConfirmationVO.builder()
                .confirmationRef(confirmation.getConfirmationRef())
                .confirmationStatus(confirmation.getStatus())
                .confirmationStatusLabel(label)
                .expiresAt(confirmation.getExpiresAt())
                .replayed(replayed)
                .build();
    }
}
