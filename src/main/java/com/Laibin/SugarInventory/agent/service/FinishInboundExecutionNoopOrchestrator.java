package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.domain.po.AgentFinishInboundExecutionRequest;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionControlResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class FinishInboundExecutionNoopOrchestrator {
    private final FinishInboundExecutionAttemptService attemptService;
    private final FinishInboundExecutionCompletionService completionService;

    public FinishInboundExecutionControlResultVO consume(
            String confirmationRef, String executionToken, String idempotencyKey,
            User user, String agentSessionId, Set<String> authorities) {
        FinishInboundExecutionAttemptService.BeginOutcome begin = attemptService.begin(
                confirmationRef, executionToken, idempotencyKey, user, agentSessionId, authorities);
        if (begin.replayed()) {
            return toVo(begin.request(), true, 0);
        }
        try {
            FinishInboundExecutionCompletionService.CompletionOutcome completed =
                    completionService.executeNoop(begin.request().getId());
            return toVo(completed.request(), completed.replayed(), completed.businessWrites());
        } catch (RuntimeException exception) {
            attemptService.recordRetryableFailure(begin.request().getId(), "S2_NOOP_EXECUTION_FAILED");
            AgentFinishInboundExecutionRequest failed = begin.request();
            failed.setStatus("FAILED_RETRYABLE");
            failed.setErrorCode("S2_NOOP_EXECUTION_FAILED");
            return toVo(failed, false, 0);
        }
    }

    private static FinishInboundExecutionControlResultVO toVo(
            AgentFinishInboundExecutionRequest request, boolean replayed, int businessWrites) {
        String status = request.getStatus();
        String label = switch (status == null ? "" : status) {
            case "SUCCEEDED" -> "控制面验证通过";
            case "FAILED_RETRYABLE" -> "本次未完成，可安全重试";
            case "IN_PROGRESS" -> "处理中";
            default -> "状态未知";
        };
        return FinishInboundExecutionControlResultVO.builder()
                .executionRef(request.getExecutionRef())
                .executionStatus(status)
                .executionStatusLabel(label)
                .resultCode(request.getResultCode())
                .errorCode(request.getErrorCode())
                .replayed(replayed)
                .attemptCount(request.getAttemptCount() == null ? 0 : request.getAttemptCount())
                .businessWrites(businessWrites)
                .completedAt(request.getCompletedAt())
                .build();
    }
}
