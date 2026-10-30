package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportAgentVO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportAgentMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDailyReportAgentReadServiceTest {
    private final ProductionDailyReportAgentMapper mapper = mock(ProductionDailyReportAgentMapper.class);
    private final ProductionDailyAggregateService aggregates = mock(ProductionDailyAggregateService.class);
    private final ProductionDailyReportAgentReadService service = new ProductionDailyReportAgentReadService(mapper, aggregates);

    @org.junit.jupiter.api.BeforeEach
    void emptyAggregate() {
        when(aggregates.aggregate(any(), eq(false))).thenAnswer(call -> {
            var scope = (ProductionDailyReportAgentQueryDTO) call.getArgument(0);
            var result = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO();
            result.setRequestedDays((int) java.time.temporal.ChronoUnit.DAYS.between(scope.getStartDate(), scope.getEndDate()) + 1);
            result.setSeries(List.of());
            return result;
        });
    }

    private ProductionDailyReportAgentQueryDTO query() {
        var query = new ProductionDailyReportAgentQueryDTO();
        query.setStartDate(LocalDate.of(2026, 9, 8));
        query.setEndDate(query.getStartDate());
        return query;
    }

    @Test
    void preservesSavedZeroNullAndCumulativeValuesWithoutSumming() throws Exception {
        var query = query();
        var header = new ProductionDailyReportAgentVO.Report();
        header.setReportDate(query.getStartDate());
        header.setStatusLabel("草稿");
        var row = new ProductionDailyReportAgentVO.Row();
        row.setDailyActual(BigDecimal.ZERO);
        row.setMonthQuantity(new BigDecimal("123.45"));
        row.setSectionStatusLabel("已提交");
        when(mapper.listReports(query)).thenReturn(List.of(header));
        when(mapper.countRows(query)).thenReturn(31L);
        when(mapper.pageRows(query, 0)).thenReturn(List.of(row));
        var result = service.query(query);
        assertThat(result.isEmpty()).isFalse();
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getDateRangeLabel()).isEqualTo("2026-09-08");
        assertThat(result.getRecords().get(0).getDailyActual()).isEqualByComparingTo("0");
        assertThat(result.getRecords().get(0).getConvertedTons()).isNull();
        assertThat(result.getRecords().get(0).getMonthQuantity()).isEqualByComparingTo("123.45");
        var json = new ObjectMapper().findAndRegisterModules().valueToTree(result);
        assertThat(json.has("isEmpty")).isTrue();
        assertThat(json.path("records").get(0).path("convertedTons").isNull()).isTrue();
    }

    @Test
    void distinguishesNoReportFromNoMatchingOrOutOfRangePage() {
        var query = query();
        when(mapper.listReports(query)).thenReturn(List.of());
        when(mapper.pageRows(query, 0)).thenReturn(List.of());
        assertThat(service.query(query).isEmpty()).isTrue();
        when(mapper.listReports(query)).thenReturn(List.of(new ProductionDailyReportAgentVO.Report()));
        assertThat(service.query(query).isEmpty()).isFalse();
    }

    @Test
    void rejectsInvalidRangePaginationAndUnknownDepartmentBeforeReading() {
        var query = query();
        query.setEndDate(query.getStartDate().plusDays(31));
        assertThatThrownBy(() -> service.query(query)).isInstanceOf(BusinessException.class);
        query.setEndDate(query.getStartDate());
        query.setDepartmentName("不存在车间");
        assertThatThrownBy(() -> service.query(query)).isInstanceOf(BusinessException.class);
        query.setDepartmentName(null);
        query.setSize(51);
        assertThatThrownBy(() -> service.query(query)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void mapperUsesBoundFiltersSavedTablesAndStablePagination() {
        var config = new Configuration();
        config.addMapper(ProductionDailyReportAgentMapper.class);
        var query = query();
        query.setItemQuery("' OR 1=1 --");
        query.setDepartmentName("多晶车间");
        var sql = config.getMappedStatement(ProductionDailyReportAgentMapper.class.getName() + ".pageRows")
                .getBoundSql(Map.of("query", query, "offset", 20)).getSql();
        assertThat(sql).contains("production_daily_metric_value", "production_daily_product_line", "UNION ALL", "LOCATE(?,", "LIMIT ?, ?");
        assertThat(sql).doesNotContain(query.getItemQuery(), "production_order_output", "COALESCE", "SUM(");
    }

    @Test
    void overviewIsIndependentOfPageAndExplainsPartialCoverage() {
        var query = query();
        query.setEndDate(query.getStartDate().plusDays(2));
        query.setPage(2);
        var total = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Series();
        total.setLabel("总产量");
        total.setQuantity(new BigDecimal("120"));
        total.setRecordCount(3);
        total.setQuantityRows(2);
        total.setObservedDays(2);
        total.setTonnageRows(1);
        total.setDraftRows(1);
        total.setCoverageLabel("数量已记录2/3行，覆盖2/3天；折吨已记录1/3行；含1行未提交车间数据");
        total.setPoints(List.of(new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Point()));
        var aggregate = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO();
        aggregate.setSeries(List.of(total));
        aggregate.setRequestedDays(3);
        when(aggregates.aggregate(query, false)).thenReturn(aggregate);
        when(mapper.listReports(query)).thenReturn(List.of());
        when(mapper.pageRows(query, 20)).thenReturn(List.of());
        var result = service.query(query);
        assertThat(result.getOverview().get(0).getQuantity()).isEqualByComparingTo("120");
        assertThat(result.getOverview().get(0).getCoverageLabel()).contains("2/3行", "2/3天", "1/3行", "未提交");
        assertThat(result.getOverview().get(0).getConvertedTons()).isNull();
        assertThat(result.getRequestedDays()).isEqualTo(3);
        verify(aggregates).aggregate(query, false);
        assertThat(result.getOverview().get(0).getClass()).isEqualTo(ProductionDailyReportAgentVO.Overview.class);
    }

    @Test
    void energyCoverageDoesNotMisrepresentGenericTonnageColumns() {
        var query = query();
        var energy = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Series();
        energy.setSummaryType("全厂能耗");
        energy.setUnit("Kw.h");
        energy.setRecordCount(1);
        energy.setQuantityRows(1);
        energy.setObservedDays(1);
        when(mapper.listReports(query)).thenReturn(List.of());
        when(mapper.pageRows(query, 0)).thenReturn(List.of());
        energy.setCoverageLabel("数量已记录1/1行，覆盖1/1天");
        var aggregate = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO();
        aggregate.setSeries(List.of(energy));
        when(aggregates.aggregate(query, false)).thenReturn(aggregate);
        assertThat(service.query(query).getOverview().get(0).getCoverageLabel()).doesNotContain("折吨");
    }

    @Test
    void overviewBoundsDoNotSilentlyClaimFullCoverage() {
        var query = query();
        when(mapper.listReports(query)).thenReturn(List.of());
        when(mapper.pageRows(query, 0)).thenReturn(List.of());
        var aggregate = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO();
        aggregate.setTruncated(true);
        aggregate.setSeries(java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Series()).toList());
        when(aggregates.aggregate(query, false)).thenReturn(aggregate);
        var result = service.query(query);
        assertThat(result.getOverview()).hasSize(10);
        assertThat(result.isOverviewTruncated()).isTrue();
    }

    @Test
    void overviewSqlSeparatesMetricsFromProductStagesAndNeverSumsRatesOrCumulatives() {
        var config = new Configuration();
        config.addMapper(com.Laibin.SugarInventory.production.mapper.ProductionDailyAggregateMapper.class);
        var query = query();
        query.setItemQuery("黄冰糖");
        query.setDepartmentName("多晶车间");
        var parameters = Map.of("query", query, "productOnly", true);
        var prefix = com.Laibin.SugarInventory.production.mapper.ProductionDailyAggregateMapper.class.getName() + ".";
        var sql = config.getMappedStatement(prefix + "listSeries").getBoundSql(parameters).getSql();
        assertThat(sql).contains("FACTORY_TOTAL_OUTPUT", "LOCATE(?,", "AND 1=0", "v.product_status_snapshot", "LIMIT 11");
        assertThat(sql).doesNotContain("_RATE'", "SUBTOTAL", "month_quantity", "year_tons", "COALESCE", "production_order", "黄冰糖");
    }

    @Test
    void methodSecurityRequiresDailyReportPermissionNotOrderPermission() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext(SecurityConfig.class)) {
            var secured = context.getBean(ProductionDailyReportAgentReadService.class);
            var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "user", "unused", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("production:order:view")));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
            assertThatThrownBy(() -> secured.query(query())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verifyNoInteractions(context.getBean(ProductionDailyReportAgentMapper.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class SecurityConfig {
        @org.springframework.context.annotation.Bean
        ProductionDailyReportAgentMapper mapper() { return mock(ProductionDailyReportAgentMapper.class); }
        @org.springframework.context.annotation.Bean
        ProductionDailyReportAgentReadService service(ProductionDailyReportAgentMapper mapper) {
            return new ProductionDailyReportAgentReadService(mapper, mock(ProductionDailyAggregateService.class));
        }
    }
}
