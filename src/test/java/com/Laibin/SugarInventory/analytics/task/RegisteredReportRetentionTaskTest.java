package com.Laibin.SugarInventory.analytics.task;

import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportRetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisteredReportRetentionTaskTest {
    @Test
    void scheduledTaskRunsBoundedCleanup() {
        RegisteredReportRetentionService service = mock(RegisteredReportRetentionService.class);
        when(service.cleanupExpiredBatch(250)).thenReturn(
                new RegisteredReportRetentionService.CleanupResult(
                        "report_cleanup_1234567890abcdef", 2, 2));
        RegisteredReportRetentionTask task = new RegisteredReportRetentionTask(service);
        ReflectionTestUtils.setField(task, "cleanupBatchSize", 250);

        task.cleanupExpiredReports();

        verify(service).cleanupExpiredBatch(250);
    }

    @Test
    void scheduledTaskRecordsFailureWithoutEscapingToScheduler() {
        RegisteredReportRetentionService service = mock(RegisteredReportRetentionService.class);
        IllegalStateException failure = new IllegalStateException("cleanup failed");
        doThrow(failure).when(service).cleanupExpiredBatch(1000);
        RegisteredReportRetentionTask task = new RegisteredReportRetentionTask(service);
        ReflectionTestUtils.setField(task, "cleanupBatchSize", 1000);

        task.cleanupExpiredReports();

        verify(service).recordFailure(1000, failure);
    }
}
