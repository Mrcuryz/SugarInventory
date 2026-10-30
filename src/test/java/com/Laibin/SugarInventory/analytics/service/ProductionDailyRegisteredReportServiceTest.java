package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.impl.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO;
import com.Laibin.SugarInventory.production.service.ProductionDailyAggregateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDailyRegisteredReportServiceTest {
    private final ProductionDailyAggregateService aggregates = mock(ProductionDailyAggregateService.class);
    private final ProductionDailyRegisteredReportService service = new ProductionDailyRegisteredReportService(aggregates);

    @Test
    void independentDefinitionUsesProductOnlyFilterAndWarnsOnMissingCoverageDraftsAndTruncation() {
        var result = new ProductionDailyAggregateVO();
        result.setRequestedDays(3); result.setTruncated(true);
        var series = new ProductionDailyAggregateVO.Series();
        series.setDepartmentName("多晶车间"); series.setLabel("冰糖产量"); series.setTonnageApplicable(true);
        series.setQuantityRows(2); series.setRecordCount(2); series.setObservedDays(2); series.setDraftRows(1);
        series.setCoverageLabel("数量已记录2/2行，覆盖2/3天；折吨已记录0/2行，覆盖0/3天；含1行未提交车间数据");
        result.setSeries(List.of(series));
        when(aggregates.aggregate(any(), eq(true))).thenReturn(result);
        var query = query(); query.setDepartmentName(" 多晶车间 "); query.setProductQuery(" 黄冰糖 ");
        var report = service.run(query);
        var captured = ArgumentCaptor.forClass(ProductionDailyReportAgentQueryDTO.class);
        verify(aggregates).aggregate(captured.capture(), eq(true));
        assertThat(captured.getValue().getItemQuery()).isEqualTo("黄冰糖");
        assertThat(captured.getValue().getDepartmentName()).isEqualTo("多晶车间");
        assertThat(report.getDataScope()).isEqualTo("SAVED_PRODUCTION_DAILY_REPORT");
        assertThat(report.getMetrics()).isNull();
        assertThat(report.getProductionDailyReport()).isSameAs(result);
        assertThat(report.getDataQuality().isPartial()).isTrue();
        assertThat(report.getDataQuality().getNotes()).anyMatch(n -> n.contains("前10个"))
                .anyMatch(n -> n.contains("0/3天") && n.contains("未提交"));
        assertThat(report.getFiltersApplied()).containsEntry("sourceLabel", "生产日报");
    }

    @Test
    void emptyReportIsNotReportedAsZeroAndSingleDateLabelIsNatural() {
        var aggregate = new ProductionDailyAggregateVO(); aggregate.setSeries(List.of()); aggregate.setRequestedDays(1);
        when(aggregates.aggregate(any(), eq(true))).thenReturn(aggregate);
        var query = query(); query.setEndDate(query.getStartDate());
        var result = service.run(query);
        assertThat(result.getDateRangeLabel()).isEqualTo("2026-09-05");
        assertThat(result.getDataQuality().getNotes()).anyMatch(n -> n.contains("不表示产量为0"));
    }

    @Test
    void unsupportedFiltersComparisonVersionAndFutureDatesRejectBeforeDataAccess() {
        var query = query(); query.setMetricKey("color_value");
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class);
        query.setMetricKey(null); query.setTaskType("OUT");
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class);
        query.setTaskType(null); query.setComparisonMode("UNSUPPORTED");
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class);
        query.setComparisonMode(null); query.setComparisonStartDate(query.getStartDate().minusDays(3));
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class);
        query.setComparisonStartDate(null); query.setReportVersion(2);
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class);
        query.setReportVersion(1); query.setStartDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
        query.setEndDate(query.getStartDate());
        assertThatThrownBy(() -> service.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("未来日期");
        verifyNoInteractions(aggregates);
    }

    @Test
    void entryMethodAllowsOnlyDailyReportPermissionAndDoesNotGrantOrderQueries() {
        var delegate = mock(ProductionDailyRegisteredReportService.class);
        var target = new RegisteredReportServiceImpl(null, null, null, null, null, null, null, null,
                new RegisteredReportComparisonAssembler(), delegate);
        var proxy = new ProxyFactory(target);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        var secured = (RegisteredReportService) proxy.getProxy();
        var query = query();
        var answer = RegisteredReportRunVO.builder().reportDefinitionId(query.getReportDefinitionId()).build();
        when(delegate.run(query)).thenReturn(answer);
        try {
            authenticate("production:order:view");
            assertThatThrownBy(() -> secured.run(query)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(delegate);
            authenticate("production:daily-report:view");
            assertThat(secured.run(query)).isSameAs(answer);
            query.setReportDefinitionId("daily_production_overview_v1");
            assertThatThrownBy(() -> secured.run(query)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void otherDefinitionsRejectDepartmentInsteadOfSilentlyIgnoringIt() {
        var target = new RegisteredReportServiceImpl(null, null, null, null, null, null, null, null,
                new RegisteredReportComparisonAssembler(), service);
        var query = query(); query.setReportDefinitionId("daily_production_overview_v1"); query.setDepartmentName("多晶车间");
        assertThatThrownBy(() -> target.run(query)).isInstanceOf(BusinessException.class).hasMessageContaining("车间筛选仅适用");
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("local-test", "unused",
                List.of(new SimpleGrantedAuthority(authority))));
    }
    private static RegisteredReportRunQueryDTO query() {
        var query = new RegisteredReportRunQueryDTO(); query.setReportDefinitionId(ProductionDailyRegisteredReportService.REPORT_ID);
        query.setReportVersion(1); query.setStartDate(LocalDate.of(2026, 9, 5)); query.setEndDate(query.getStartDate().plusDays(2));
        return query;
    }
}
