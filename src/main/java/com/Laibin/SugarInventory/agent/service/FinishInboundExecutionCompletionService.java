package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionPreview;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionConfirmationMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionPreviewMapper;
import com.Laibin.SugarInventory.mapper.AgentFinishInboundExecutionRequestMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionCompletionService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AgentFinishInboundExecutionRequestMapper requestMapper;
    private final AgentFinishInboundExecutionConfirmationMapper confirmationMapper;
    private final AgentFinishInboundExecutionPreviewMapper previewMapper;
    private final FinishInboundExecutionAuditService auditService;
    private final FinishInboundExecutionNoopAdapter noopAdapter;
    private final FinishInboundExecutionDomainAdapter domainAdapter;
    private final FinishInboundExecutionStateVerifier stateVerifier;
    private final ObjectMapper objectMapper;

    private static final String S3_REQUIRED_PERMISSIONS =
            "agent:finish-inbound:execute,task:confirm,task:view";
    private static final Set<String> S3_REQUIRED_PERMISSION_SET = Set.of(
            "agent:finish-inbound:execute", "task:view", "task:confirm");

    @Transactional
    public CompletionOutcome executeNoop(Long requestId) {
        AgentFinishInboundExecutionRequest requestHint = requestMapper.selectById(requestId);
        if (requestHint == null) throw new BusinessException(404, "成品入库执行请求不存在");
        AgentFinishInboundExecutionConfirmation confirmation =
                confirmationMapper.selectByRefForUpdate(requestHint.getConfirmationRef());
        AgentFinishInboundExecutionRequest request = requestMapper.selectByIdForUpdate(requestId);
        if (request == null) throw new BusinessException(404, "成品入库执行请求不存在");
        if ("SUCCEEDED".equals(request.getStatus())) {
            return new CompletionOutcome(request, true, 0);
        }
        if (!"IN_PROGRESS".equals(request.getStatus())) {
            throw new BusinessException(409, "成品入库执行请求当前不能完成");
        }
        if (confirmation == null || !"CONFIRMED".equals(confirmation.getStatus())) {
            throw new BusinessException(409, "成品入库执行确认已经失效");
        }

        FinishInboundExecutionNoopAdapter.AdapterResult adapterResult =
                noopAdapter.execute(request.getConfirmationRef(), request.getExecutionRef());
        if (adapterResult.businessWrites() != 0) {
            throw new IllegalStateException("S2 no-op adapter must not write business data");
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        auditService.append(request.getConfirmationRef(), request.getExecutionRef(),
                request.getOwnerUserId(), request.getAgentSessionId(), "EXECUTION_SUCCEEDED",
                "IN_PROGRESS", "SUCCEEDED", adapterResult.resultCode(), null,
                request.getExecutionRef() + "|businessWrites=0");
        request.setStatus("SUCCEEDED");
        request.setResultCode(adapterResult.resultCode());
        request.setResultJson(writeSafeResult(adapterResult));
        request.setErrorCode(null);
        request.setCompletedAt(now);
        request.setUpdatedAt(now);
        requestMapper.updateById(request);

        confirmation.setStatus("CONSUMED");
        confirmation.setConsumedAt(now);
        confirmation.setUpdatedAt(now);
        confirmationMapper.updateById(confirmation);
        AgentFinishInboundExecutionPreview preview = previewMapper.selectById(confirmation.getPreviewId());
        if (preview != null) {
            preview.setStatus("CONSUMED");
            preview.setConsumedAt(now);
            previewMapper.updateById(preview);
        }
        return new CompletionOutcome(request, false, adapterResult.businessWrites());
    }

    @Transactional
    public CompletionOutcome executeDomain(
            Long requestId, User user, Set<String> authorities) {
        requireDomainActor(user, authorities);
        AgentFinishInboundExecutionRequest requestHint = requestMapper.selectById(requestId);
        if (requestHint == null) throw new BusinessException(404, "成品入库执行请求不存在");
        AgentFinishInboundExecutionConfirmation confirmation =
                confirmationMapper.selectByRefForUpdate(requestHint.getConfirmationRef());
        AgentFinishInboundExecutionRequest request = requestMapper.selectByIdForUpdate(requestId);
        if (request == null) throw new BusinessException(404, "成品入库执行请求不存在");
        if ("SUCCEEDED".equals(request.getStatus())) {
            return new CompletionOutcome(request, true, 0);
        }
        if (!"IN_PROGRESS".equals(request.getStatus())) {
            throw new BusinessException(409, "成品入库执行请求当前不能完成");
        }
        if (confirmation == null || !"CONFIRMED".equals(confirmation.getStatus())
                || !S3_REQUIRED_PERMISSIONS.equals(confirmation.getRequiredPermissions())) {
            throw new BusinessException(409, "成品入库执行确认已经失效");
        }
        AgentFinishInboundExecutionPreview preview =
                previewMapper.selectById(confirmation.getPreviewId());
        stateVerifier.verifyUnchanged(preview, user);
        FinishInboundExecutionDomainAdapter.AdapterResult adapterResult =
                domainAdapter.execute(preview, user);

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        auditService.append(request.getConfirmationRef(), request.getExecutionRef(),
                request.getOwnerUserId(), request.getAgentSessionId(), "EXECUTION_SUCCEEDED",
                "IN_PROGRESS", "SUCCEEDED", adapterResult.resultCode(), null,
                request.getExecutionRef() + "|affectedPalletCount="
                        + adapterResult.affectedPalletCount());
        request.setStatus("SUCCEEDED");
        request.setResultCode(adapterResult.resultCode());
        request.setResultJson(writeSafeDomainResult(adapterResult));
        request.setErrorCode(null);
        request.setCompletedAt(now);
        request.setUpdatedAt(now);
        requestMapper.updateById(request);

        confirmation.setStatus("CONSUMED");
        confirmation.setConsumedAt(now);
        confirmation.setUpdatedAt(now);
        confirmationMapper.updateById(confirmation);
        if (preview != null) {
            preview.setStatus("CONSUMED");
            preview.setConsumedAt(now);
            previewMapper.updateById(preview);
        }
        return new CompletionOutcome(request, false, adapterResult.affectedPalletCount());
    }

    private String writeSafeResult(FinishInboundExecutionNoopAdapter.AdapterResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultCode", result.resultCode());
        payload.put("businessWrites", result.businessWrites());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存成品入库控制面结果失败", exception);
        }
    }

    private String writeSafeDomainResult(FinishInboundExecutionDomainAdapter.AdapterResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resultCode", result.resultCode());
        payload.put("affectedPalletCount", result.affectedPalletCount());
        payload.put("palletCodes", List.copyOf(result.palletCodes()));
        payload.put("resultLabel", "成品入库已完成");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("保存成品入库执行结果失败", exception);
        }
    }

    private static void requireDomainActor(User user, Set<String> authorities) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException(401, "当前用户未登录");
        }
        if (authorities == null || !authorities.containsAll(S3_REQUIRED_PERMISSION_SET)) {
            throw new BusinessException(403, "当前用户没有执行成品入库的权限");
        }
    }

    public record CompletionOutcome(
            AgentFinishInboundExecutionRequest request, boolean replayed, int businessWrites) {
    }
}
