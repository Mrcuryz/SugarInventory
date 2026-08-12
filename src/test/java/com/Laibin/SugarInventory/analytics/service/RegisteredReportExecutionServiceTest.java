package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportArchiveService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportExecutionAuditService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportExecutionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisteredReportExecutionServiceTest {
    @Test
    void runsPersistsAndRecordsSuccessfulExecution() {
        RegisteredReportService reportService = mock(RegisteredReportService.class);
        RegisteredReportArchiveService archiveService = mock(RegisteredReportArchiveService.class);
        RegisteredReportExecutionAuditService auditService =
                mock(RegisteredReportExecutionAuditService.class);
        RegisteredReportExecutionService service = new RegisteredReportExecutionService(
                reportService, archiveService, auditService);
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        RegisteredReportRunVO report = RegisteredReportRunVO.builder().build();
        RegisteredReportExecutionAuditService.ExecutionStart execution =
                new RegisteredReportExecutionAuditService.ExecutionStart(
                        "daily_production_overview_v1", 7,
                        LocalDateTime.of(2026, 8, 12, 12, 0), 1L);
        when(auditService.start(query, 7)).thenReturn(execution);
        when(reportService.run(query)).thenReturn(report);
        when(archiveService.persist(report, 7, "陈思聪")).thenReturn(report);

        RegisteredReportRunVO result = service.runAndArchive(query, 7, "陈思聪");

        assertThat(result).isSameAs(report);
        verify(auditService).recordSuccess(execution, report);
    }

    @Test
    void recordsOriginalFailureAndDoesNotReplaceItWhenAuditAlsoFails() {
        RegisteredReportService reportService = mock(RegisteredReportService.class);
        RegisteredReportArchiveService archiveService = mock(RegisteredReportArchiveService.class);
        RegisteredReportExecutionAuditService auditService =
                mock(RegisteredReportExecutionAuditService.class);
        RegisteredReportExecutionService service = new RegisteredReportExecutionService(
                reportService, archiveService, auditService);
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        RegisteredReportExecutionAuditService.ExecutionStart execution =
                new RegisteredReportExecutionAuditService.ExecutionStart(
                        "daily_production_overview_v1", 9,
                        LocalDateTime.of(2026, 8, 12, 12, 0), 1L);
        IllegalStateException reportFailure = new IllegalStateException("report failed");
        IllegalStateException auditFailure = new IllegalStateException("audit failed");
        when(auditService.start(query, 9)).thenReturn(execution);
        when(reportService.run(query)).thenThrow(reportFailure);
        org.mockito.Mockito.doThrow(auditFailure)
                .when(auditService).recordFailure(execution, reportFailure);

        assertThatThrownBy(() -> service.runAndArchive(query, 9, "审核员"))
                .isSameAs(reportFailure);
        assertThat(reportFailure.getSuppressed()).containsExactly(auditFailure);
        verify(auditService).recordFailure(execution, reportFailure);
    }
}
