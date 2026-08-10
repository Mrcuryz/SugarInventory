package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.FinishInboundExecutionControlCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionConfirmationMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionAttemptService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String S2_REQUIRED_PERMISSIONS = "task:confirm,task:view";
    private static final Set<String> S2_REQUIRED_PERMISSION_SET = Set.of("task:view", "task:confirm");
    private static final String S2_REQUEST_KIND = "S2_NOOP_CONTROL_VALIDATION_V1";
    private static final String S3_REQUIRED_PERMISSIONS =
            "agent:finish-inbound:execute,task:confirm,task:view";
    private static final Set<String> S3_REQUIRED_PERMISSION_SET = Set.of(
            "agent:finish-inbound:execute", "task:view", "task:confirm");
    private static final String S3_REQUEST_KIND = "S3_FINISH_INBOUND_DOMAIN_EXECUTION_V1";

    private final AgentFinishInboundExecutionConfirmationMapper confirmationMapper;
    private final AgentFinishInboundExecutionPreviewMapper previewMapper;
    private final AgentFinishInboundExecutionRequestMapper requestMapper;
    private final FinishInboundExecutionStateVerifier stateVerifier;
    private final FinishInboundExecutionControlCodec codec;
    private final FinishInboundExecutionAuditService auditService;

    @Transactional(noRollbackFor = BusinessException.class)
    public BeginOutcome begin(String confirmationRef, String executionToken, String idempotencyKey,
                              User user, String agentSessionId, Set<String> authorities) {
        return beginInternal(confirmationRef, executionToken, idempotencyKey, user, agentSessionId,
                authorities, S2_REQUIRED_PERMISSION_SET, S2_REQUIRED_PERMISSIONS, S2_REQUEST_KIND);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public BeginOutcome beginDomainExecution(
            String confirmationRef, String executionToken, String idempotencyKey,
            User user, String agentSessionId, Set<String> authorities) {
        return beginInternal(confirmationRef, executionToken, idempotencyKey, user, agentSessionId,
                authorities, S3_REQUIRED_PERMISSION_SET, S3_REQUIRED_PERMISSIONS, S3_REQUEST_KIND);
    }

    private BeginOutcome beginInternal(
            String confirmationRef, String executionToken, String idempotencyKey,
            User user, String agentSessionId, Set<String> authorities,
            Set<String> requiredPermissionSet, String requiredPermissions, String requestKind) {
        requireActor(user, agentSessionId, authorities, requiredPermissionSet);
        AgentFinishInboundExecutionConfirmation confirmation =
                confirmationMapper.selectByRefForUpdate(confirmationRef);
        requireOwned(confirmation, user.getId(), agentSessionId, requiredPermissions);
        if (!codec.matchesHash(executionToken, confirmation.getTokenSha256())) {
            throw new BusinessException(403, "执行令牌无效或不属于本次确认");
        }
        if (!codec.matchesHash(idempotencyKey, confirmation.getIdempotencyKeySha256())) {
            throw new BusinessException(409, "幂等键与本次确认不匹配");
        }
        String requestHash = FinishInboundExecutionControlCodec.sha256(
                requestKind + "|" + confirmationRef + "|"
                        + confirmation.getPreviewContentSha256() + "|"
                        + confirmation.getPreviewStateDigest());
        AgentFinishInboundExecutionRequest request =
                requestMapper.selectByConfirmationForUpdate(confirmation.getId());
        if (request != null) {
            verifySameRequest(request, idempotencyKey, requestHash);
            if ("SUCCEEDED".equals(request.getStatus())) {
                return new BeginOutcome(request, true);
            }
            if ("IN_PROGRESS".equals(request.getStatus())) {
                throw new BusinessException(409, "本次执行请求正在处理中，请稍后按相同幂等键重试");
            }
        }

        if (!"CONFIRMED".equals(confirmation.getStatus())) {
            throw new BusinessException(409, statusMessage(confirmation.getStatus()));
        }
        if (isExpired(confirmation)) {
            transitionConfirmation(confirmation, "EXPIRED", "CONFIRMATION_EXPIRED");
            throw new BusinessException(410, "本次确认已过期，请重新预览并确认");
        }
        AgentFinishInboundExecutionPreview preview = previewMapper.selectById(confirmation.getPreviewId());
        try {
            stateVerifier.verifyUnchanged(preview, user);
        } catch (BusinessException exception) {
            transitionConfirmation(confirmation, "INVALIDATED", "STATE_INVALIDATED");
            if (preview != null) {
                preview.setStatus("INVALIDATED");
                previewMapper.updateById(preview);
            }
            throw exception;
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String fromStatus = request == null ? null : request.getStatus();
        if (request == null) {
            request = new AgentFinishInboundExecutionRequest();
            request.setExecutionRef("fie1_" + UUID.randomUUID().toString().replace("-", ""));
            request.setConfirmationId(confirmation.getId());
            request.setConfirmationRef(confirmationRef);
            request.setOwnerUserId(user.getId());
            request.setAgentSessionId(agentSessionId);
            request.setIdempotencyKeySha256(FinishInboundExecutionControlCodec.sha256(idempotencyKey));
            request.setRequestSha256(requestHash);
            request.setAttemptCount(1);
            request.setStatus("IN_PROGRESS");
            request.setStartedAt(now);
            request.setCreatedAt(now);
            request.setUpdatedAt(now);
            requestMapper.insert(request);
        } else {
            request.setAttemptCount((request.getAttemptCount() == null ? 0 : request.getAttemptCount()) + 1);
            request.setStatus("IN_PROGRESS");
            request.setErrorCode(null);
            request.setStartedAt(now);
            request.setCompletedAt(null);
            request.setUpdatedAt(now);
            requestMapper.updateById(request);
        }
        auditService.append(confirmationRef, request.getExecutionRef(), user.getId(), agentSessionId,
                "EXECUTION_ACCEPTED", fromStatus, "IN_PROGRESS", null, null,
                request.getExecutionRef() + "|" + requestHash);
        return new BeginOutcome(request, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetryableFailure(Long requestId, String errorCode) {
        AgentFinishInboundExecutionRequest request = requestMapper.selectByIdForUpdate(requestId);
        if (request == null || "SUCCEEDED".equals(request.getStatus())) return;
        String from = request.getStatus();
        request.setStatus("FAILED_RETRYABLE");
        request.setErrorCode(limit(errorCode, 80));
        request.setCompletedAt(LocalDateTime.now(BUSINESS_ZONE));
        request.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        requestMapper.updateById(request);
        auditService.append(request.getConfirmationRef(), request.getExecutionRef(),
                request.getOwnerUserId(), request.getAgentSessionId(), "EXECUTION_FAILED",
                from, "FAILED_RETRYABLE", null, request.getErrorCode(), request.getExecutionRef());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFinalBusinessFailure(Long requestId, String errorCode) {
        AgentFinishInboundExecutionRequest request = requestMapper.selectById(requestId);
        if (request == null) return;
        AgentFinishInboundExecutionConfirmation confirmation =
                confirmationMapper.selectByRefForUpdate(request.getConfirmationRef());
        request = requestMapper.selectByIdForUpdate(requestId);
        if (request == null || "SUCCEEDED".equals(request.getStatus())) return;
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        String from = request.getStatus();
        request.setStatus("FAILED_FINAL");
        request.setErrorCode(limit(errorCode, 80));
        request.setCompletedAt(now);
        request.setUpdatedAt(now);
        requestMapper.updateById(request);
        if (confirmation != null && !"CONSUMED".equals(confirmation.getStatus())) {
            confirmation.setStatus("INVALIDATED");
            confirmation.setUpdatedAt(now);
            confirmationMapper.updateById(confirmation);
            AgentFinishInboundExecutionPreview preview =
                    previewMapper.selectById(confirmation.getPreviewId());
            if (preview != null && !"CONSUMED".equals(preview.getStatus())) {
                preview.setStatus("INVALIDATED");
                previewMapper.updateById(preview);
            }
        }
        auditService.append(request.getConfirmationRef(), request.getExecutionRef(),
                request.getOwnerUserId(), request.getAgentSessionId(), "EXECUTION_REJECTED",
                from, "FAILED_FINAL", null, request.getErrorCode(), request.getExecutionRef());
    }

    private void transitionConfirmation(AgentFinishInboundExecutionConfirmation confirmation,
                                        String status, String eventType) {
        String from = confirmation.getStatus();
        confirmation.setStatus(status);
        confirmation.setUpdatedAt(LocalDateTime.now(BUSINESS_ZONE));
        confirmationMapper.updateById(confirmation);
        AgentFinishInboundExecutionPreview preview = previewMapper.selectById(confirmation.getPreviewId());
        if (preview != null) {
            preview.setStatus(status);
            previewMapper.updateById(preview);
        }
        auditService.append(confirmation.getConfirmationRef(), null, confirmation.getOwnerUserId(),
                confirmation.getAgentSessionId(), eventType, from, status, null, eventType,
                confirmation.getConfirmationRef());
    }

    private void verifySameRequest(AgentFinishInboundExecutionRequest request,
                                   String idempotencyKey, String requestHash) {
        if (!codec.matchesHash(idempotencyKey, request.getIdempotencyKeySha256())
                || !Objects.equals(requestHash, request.getRequestSha256())) {
            throw new BusinessException(409, "相同确认不能使用不同的幂等键或请求内容");
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
            throw new BusinessException(403, "当前用户没有执行成品入库的权限");
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

    private static String statusMessage(String status) {
        return switch (status == null ? "" : status) {
            case "REVOKED" -> "本次确认已撤销，请重新预览并确认";
            case "CONSUMED" -> "本次确认已经使用";
            case "EXPIRED" -> "本次确认已过期，请重新预览并确认";
            default -> "本次确认已失效，请重新预览并确认";
        };
    }

    private static String limit(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record BeginOutcome(AgentFinishInboundExecutionRequest request, boolean replayed) {
    }
}
