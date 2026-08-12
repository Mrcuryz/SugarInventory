package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExecutionRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExecutionRunMapper;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportExecutionAuditService;
import com.Laibin.SugarInventory.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegisteredReportExecutionAuditServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void successRecordsOnlyOperationalMetadataAndPartialFlag() {
        RegisteredReportExecutionRunMapper mapper =
                mock(RegisteredReportExecutionRunMapper.class);
        RegisteredReportExecutionAuditService service =
                new RegisteredReportExecutionAuditService(mapper, CLOCK);
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId("quality_metric_trend_v1");
        RegisteredReportRunVO report = RegisteredReportRunVO.builder()
                .dataQuality(RegisteredReportDataQualityVO.builder().partial(true).build())
                .build();

        RegisteredReportExecutionAuditService.ExecutionStart start = service.start(query, 7);
        service.recordSuccess(start, report);

        ArgumentCaptor<RegisteredReportExecutionRunPO> captor =
                ArgumentCaptor.forClass(RegisteredReportExecutionRunPO.class);
        verify(mapper).insert(captor.capture());
        RegisteredReportExecutionRunPO audit = captor.getValue();
        assertEquals("quality_metric_trend_v1", audit.getReportDefinitionId());
        assertEquals(7, audit.getOwnerUserId());
        assertEquals("SUCCEEDED", audit.getStatus());
        assertTrue(audit.getPartialData());
        assertTrue(audit.getDurationMs() >= 0);
        assertEquals(null, audit.getFailureSummary());
    }

    @Test
    void separatesExpectedRejectionsFromTechnicalFailuresAndHidesDetails() {
        RegisteredReportExecutionRunMapper mapper =
                mock(RegisteredReportExecutionRunMapper.class);
        RegisteredReportExecutionAuditService service =
                new RegisteredReportExecutionAuditService(mapper, CLOCK);
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId("daily_production_overview_v1");

        service.recordFailure(service.start(query, 9),
                new BusinessException(400, "日期范围无效\n请重新选择"));
        service.recordFailure(service.start(query, 9),
                new AccessDeniedException("sensitive policy expression"));
        service.recordFailure(service.start(query, 9),
                new IllegalStateException("jdbc:mysql://secret"));

        ArgumentCaptor<RegisteredReportExecutionRunPO> captor =
                ArgumentCaptor.forClass(RegisteredReportExecutionRunPO.class);
        verify(mapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        RegisteredReportExecutionRunPO business = captor.getAllValues().get(0);
        RegisteredReportExecutionRunPO authorization = captor.getAllValues().get(1);
        RegisteredReportExecutionRunPO technical = captor.getAllValues().get(2);
        assertEquals("REJECTED", business.getStatus());
        assertEquals("BUSINESS_400", business.getFailureCode());
        assertEquals("日期范围无效 请重新选择", business.getFailureSummary());
        assertFalse(business.getPartialData());
        assertEquals("REJECTED", authorization.getStatus());
        assertEquals("AUTHORIZATION_DENIED", authorization.getFailureCode());
        assertEquals("当前用户没有运行该报表的权限", authorization.getFailureSummary());
        assertEquals("FAILED", technical.getStatus());
        assertEquals("REPORT_EXECUTION_FAILED", technical.getFailureCode());
        assertEquals("报表运行发生技术异常", technical.getFailureSummary());
    }
}
