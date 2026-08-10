package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionDomainOrchestrator {
    private final FinishInboundExecutionAttemptService attemptService;
    private final FinishInboundExecutionCompletionService completionService;
    private final ObjectMapper objectMapper;

    public FinishInboundExecutionResultVO execute(
            String confirmationRef, String executionToken, String idempotencyKey,
            User user, String agentSessionId, Set<String> authorities) {
        FinishInboundExecutionAttemptService.BeginOutcome begin =
                attemptService.beginDomainExecution(confirmationRef, executionToken, idempotencyKey,
                        user, agentSessionId, authorities);
        if (begin.replayed()) {
            return toVo(begin.request(), true);
        }
        try {
            FinishInboundExecutionCompletionService.CompletionOutcome completed =
                    completionService.executeDomain(begin.request().getId(), user, authorities);
            return toVo(completed.request(), completed.replayed());
        } catch (BusinessException exception) {
            attemptService.recordFinalBusinessFailure(begin.request().getId(),
                    businessErrorCode(exception));
            throw exception;
        } catch (RuntimeException exception) {
            attemptService.recordRetryableFailure(begin.request().getId(),
                    "FINISH_INBOUND_EXECUTION_TEMPORARILY_UNAVAILABLE");
            throw new BusinessException(500, "本次成品入库未完成，可使用同一确认安全重试");
        }
    }

    private FinishInboundExecutionResultVO toVo(
            AgentFinishInboundExecutionRequest request, boolean replayed) {
        JsonNode result = readResult(request.getResultJson());
        List<String> palletCodes = new ArrayList<>();
        JsonNode codes = result.path("palletCodes");
        if (codes.isArray()) {
            codes.forEach(code -> {
                String value = code.asText("").trim();
                if (!value.isEmpty()) palletCodes.add(value);
            });
        }
        int affected = result.path("affectedPalletCount").asInt(palletCodes.size());
        return FinishInboundExecutionResultVO.builder()
                .statusLabel("成品入库已完成")
                .affectedPalletCount(affected)
                .palletCodes(List.copyOf(palletCodes))
                .replayed(replayed)
                .completedAt(request.getCompletedAt())
                .build();
    }

    private JsonNode readResult(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("已提交的成品入库结果缺少安全摘要");
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("读取成品入库执行结果失败", exception);
        }
    }

    private static String businessErrorCode(BusinessException exception) {
        return switch (exception.getCode()) {
            case 401 -> "AUTHENTICATION_REQUIRED";
            case 403 -> "EXECUTION_PERMISSION_DENIED";
            case 404 -> "EXECUTION_CONTEXT_NOT_FOUND";
            case 410 -> "EXECUTION_CONFIRMATION_EXPIRED";
            default -> "BUSINESS_STATE_CHANGED";
        };
    }
}
