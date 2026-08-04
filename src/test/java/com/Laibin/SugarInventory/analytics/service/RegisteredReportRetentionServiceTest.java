package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportCleanupRunPO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportCleanupRunMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportRunMapper;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportRetentionService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisteredReportRetentionServiceTest {
    @Mock
    private RegisteredReportRunMapper reportRunMapper;
    @Mock
    private RegisteredReportCleanupRunMapper cleanupRunMapper;

    private RegisteredReportRetentionService service;

    @BeforeEach
    void setUp() {
        service = new RegisteredReportRetentionService(
                reportRunMapper,
                cleanupRunMapper,
                Clock.fixed(Instant.parse("2026-08-03T02:30:00Z"), ZoneOffset.UTC));
    }

    @Test
    void deletesExpiredSnapshotsAndCompletesAudit() {
        when(reportRunMapper.selectExpiredReportRunIds(any(), eq(1000)))
                .thenReturn(List.of("report_run_1111111111111111", "report_run_2222222222222222"));
        when(reportRunMapper.delete(any(Wrapper.class))).thenReturn(2);

        RegisteredReportRetentionService.CleanupResult result =
                service.cleanupExpiredBatch(1000);

        assertThat(result.cleanupRunId()).startsWith("report_cleanup_");
        assertThat(result.selectedReportCount()).isEqualTo(2);
        assertThat(result.deletedReportRunCount()).isEqualTo(2);
        verify(reportRunMapper).delete(any(Wrapper.class));

        ArgumentCaptor<RegisteredReportCleanupRunPO> completedAudit =
                ArgumentCaptor.forClass(RegisteredReportCleanupRunPO.class);
        verify(cleanupRunMapper).updateById(completedAudit.capture());
        assertThat(completedAudit.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(completedAudit.getValue().getSelectedReportCount()).isEqualTo(2);
        assertThat(completedAudit.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void recordsSuccessfulEmptyCleanupWithoutIssuingDeletes() {
        when(reportRunMapper.selectExpiredReportRunIds(any(), eq(1000))).thenReturn(List.of());

        RegisteredReportRetentionService.CleanupResult result =
                service.cleanupExpiredBatch(0);

        assertThat(result.selectedReportCount()).isZero();
        assertThat(result.deletedReportRunCount()).isZero();
        verify(reportRunMapper, never()).delete(any(Wrapper.class));
        verify(cleanupRunMapper).updateById(any(RegisteredReportCleanupRunPO.class));
    }

    @Test
    void failureAuditIsBoundedAndRemovesControlCharacters() {
        String message = "数据库清理失败\n" + "x".repeat(600);

        service.recordFailure(9000, new IllegalStateException(message));

        ArgumentCaptor<RegisteredReportCleanupRunPO> audit =
                ArgumentCaptor.forClass(RegisteredReportCleanupRunPO.class);
        verify(cleanupRunMapper).insert(audit.capture());
        assertThat(audit.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(audit.getValue().getBatchSize()).isEqualTo(5000);
        assertThat(audit.getValue().getFailureMessage())
                .doesNotContain("\n")
                .hasSize(500);
        assertThat(audit.getValue().getCompletedAt()).isNotNull();
    }
}
