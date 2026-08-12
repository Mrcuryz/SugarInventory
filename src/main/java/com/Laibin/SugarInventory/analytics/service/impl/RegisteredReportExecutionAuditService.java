package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExecutionRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExecutionRunMapper;
import com.Laibin.SugarInventory.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegisteredReportExecutionAuditService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern DEFINITION_ID = Pattern.compile("^[a-z0-9_]{1,100}$");

    private final RegisteredReportExecutionRunMapper executionRunMapper;
    private final Clock inventoryHistoryClock;

    public ExecutionStart start(RegisteredReportRunQueryDTO query, Integer ownerUserId) {
        return new ExecutionStart(
                safeDefinitionId(query == null ? null : query.getReportDefinitionId()),
                ownerUserId == null ? 0 : ownerUserId,
                now(),
                System.nanoTime()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(ExecutionStart start, RegisteredReportRunVO report) {
        RegisteredReportExecutionRunPO audit = baseAudit(start, "SUCCEEDED");
        audit.setPartialData(report != null
                && report.getDataQuality() != null
                && report.getDataQuality().isPartial());
        executionRunMapper.insert(audit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(ExecutionStart start, RuntimeException failure) {
        RegisteredReportExecutionRunPO audit = baseAudit(
                start, isExpectedRejection(failure) ? "REJECTED" : "FAILED");
        audit.setPartialData(false);
        audit.setFailureCode(failureCode(failure));
        audit.setFailureSummary(failureSummary(failure));
        executionRunMapper.insert(audit);
    }

    private RegisteredReportExecutionRunPO baseAudit(ExecutionStart start, String status) {
        LocalDateTime completedAt = now();
        RegisteredReportExecutionRunPO audit = new RegisteredReportExecutionRunPO();
        audit.setExecutionRef("report_execution_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        audit.setReportDefinitionId(start.reportDefinitionId());
        audit.setOwnerUserId(start.ownerUserId());
        audit.setStatus(status);
        audit.setDurationMs(Math.max(0L,
                (System.nanoTime() - start.startedNanoTime()) / 1_000_000L));
        audit.setStartedAt(start.startedAt());
        audit.setCompletedAt(completedAt);
        return audit;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(inventoryHistoryClock.withZone(BUSINESS_ZONE));
    }

    private static String safeDefinitionId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return DEFINITION_ID.matcher(normalized).matches() ? normalized : "unknown";
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof AccessDeniedException) {
            return "AUTHORIZATION_DENIED";
        }
        if (failure instanceof BusinessException businessException) {
            return "BUSINESS_" + businessException.getCode();
        }
        return "REPORT_EXECUTION_FAILED";
    }

    private static String failureSummary(RuntimeException failure) {
        if (failure instanceof AccessDeniedException) {
            return "当前用户没有运行该报表的权限";
        }
        if (!(failure instanceof BusinessException)) {
            return "报表运行发生技术异常";
        }
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return "报表运行未通过业务校验";
        }
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private static boolean isExpectedRejection(RuntimeException failure) {
        return failure instanceof BusinessException || failure instanceof AccessDeniedException;
    }

    public record ExecutionStart(
            String reportDefinitionId,
            int ownerUserId,
            LocalDateTime startedAt,
            long startedNanoTime
    ) {
    }
}
