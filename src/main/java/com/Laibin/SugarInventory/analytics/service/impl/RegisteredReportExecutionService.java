package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.RegisteredReportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisteredReportExecutionService {
    private static final Logger log =
            LoggerFactory.getLogger(RegisteredReportExecutionService.class);

    private final RegisteredReportService registeredReportService;
    private final RegisteredReportArchiveService reportArchiveService;
    private final RegisteredReportExecutionAuditService executionAuditService;

    public RegisteredReportRunVO runAndArchive(
            RegisteredReportRunQueryDTO query,
            Integer ownerUserId,
            String ownerDisplayName) {
        RegisteredReportExecutionAuditService.ExecutionStart execution =
                executionAuditService.start(query, ownerUserId);
        try {
            RegisteredReportRunVO report = registeredReportService.run(query);
            RegisteredReportRunVO persisted = reportArchiveService.persist(
                    report,
                    ownerUserId,
                    ownerDisplayName);
            recordSuccess(execution, persisted);
            return persisted;
        } catch (RuntimeException failure) {
            recordFailure(execution, failure);
            throw failure;
        }
    }

    private void recordSuccess(
            RegisteredReportExecutionAuditService.ExecutionStart execution,
            RegisteredReportRunVO report) {
        try {
            executionAuditService.recordSuccess(execution, report);
        } catch (RuntimeException auditFailure) {
            log.error("Failed to record successful registered report execution", auditFailure);
        }
    }

    private void recordFailure(
            RegisteredReportExecutionAuditService.ExecutionStart execution,
            RuntimeException failure) {
        try {
            executionAuditService.recordFailure(execution, failure);
        } catch (RuntimeException auditFailure) {
            failure.addSuppressed(auditFailure);
            log.error("Failed to record unsuccessful registered report execution", auditFailure);
        }
    }
}
