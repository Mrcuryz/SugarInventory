package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyAggregateMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionDailyAggregateServiceTest {
    private final ProductionDailyAggregateMapper mapper = mock(ProductionDailyAggregateMapper.class);
    private final ProductionDailyAggregateService service = new ProductionDailyAggregateService(mapper);

    @Test
    void sumsAllSavedDaysAndPreservesMissingDayNullAndExplicitZeroSeparately() {
        var query = query();
        var series = series("factory", true);
        when(mapper.listSeries(query, true)).thenReturn(List.of(series));
        when(mapper.dailyPoints(query, true, List.of("factory"))).thenReturn(List.of(
                point("factory", query.getStartDate().plusDays(1), "0", null, 2, 1, 0, 1),
                point("factory", query.getEndDate(), "300", "3", 1, 1, 1, 0)));
        var result = service.aggregate(query, true);
        assertThat(series.getQuantity()).isNull();
        assertThat(series.getRecordCount()).isZero();
        series = result.getSeries().get(0);
        assertThat(series.getQuantity()).isEqualByComparingTo("300");
        assertThat(series.getConvertedTons()).isEqualByComparingTo("3");
        assertThat(series.getRecordCount()).isEqualTo(3);
        assertThat(series.getQuantityRows()).isEqualTo(2);
        assertThat(series.getObservedDays()).isEqualTo(2);
        assertThat(series.getTonnageRows()).isEqualTo(1);
        assertThat(series.getTonnageObservedDays()).isEqualTo(1);
        assertThat(series.getDraftRows()).isEqualTo(1);
        assertThat(series.getCoverageLabel()).contains("数量已记录2/3行，覆盖2/3天", "折吨已记录1/3行，覆盖1/3天", "未提交");
        assertThat(series.getPoints()).hasSize(3);
        assertThat(series.getPoints().get(0).getQuantity()).isNull();
        assertThat(series.getPoints().get(0).getRecordCount()).isZero();
        assertThat(series.getPoints().get(1).getQuantity()).isEqualByComparingTo("0");
        assertThat(series.getPoints().get(1).getConvertedTons()).isNull();
        assertThat(result.isTruncated()).isFalse();
    }

    @Test
    void allNullIsNotZeroAndTonnageCanHaveDifferentObservationDates() {
        var query = query();
        var series = series("product", true);
        when(mapper.listSeries(query, false)).thenReturn(List.of(series));
        when(mapper.dailyPoints(query, false, List.of("product"))).thenReturn(List.of(
                point("product", query.getStartDate(), null, "2.25", 2, 0, 1, 2),
                point("product", query.getEndDate(), null, null, 1, 0, 0, 0)));
        series = service.aggregate(query, false).getSeries().get(0);
        assertThat(series.getQuantity()).isNull();
        assertThat(series.getConvertedTons()).isEqualByComparingTo("2.25");
        assertThat(series.getObservedDays()).isZero();
        assertThat(series.getTonnageObservedDays()).isEqualTo(1);
        assertThat(series.getPoints().get(2).getRecordCount()).isEqualTo(1);
        assertThat(series.getPoints().get(2).getQuantity()).isNull();
    }

    @Test
    void energyHasOwnUnitAndNoTonnageCoverage() {
        var query = query();
        var series = series("power", false);
        series.setUnit("Kw.h");
        when(mapper.listSeries(query, true)).thenReturn(List.of(series));
        when(mapper.dailyPoints(query, true, List.of("power"))).thenReturn(List.of(
                point("power", query.getEndDate(), "180", null, 1, 1, 0, 0)));
        series = service.aggregate(query, true).getSeries().get(0);
        assertThat(series.getUnit()).isEqualTo("Kw.h");
        assertThat(series.getConvertedTons()).isNull();
        assertThat(series.getCoverageLabel()).doesNotContain("折吨");
    }

    @Test
    void truncatesSeriesBeforeLoadingDaysAndNeverTreatsFirstDaysAsCompleteReport() {
        var query = query();
        query.setEndDate(query.getStartDate().plusDays(30));
        var candidates = IntStream.range(0, 11).mapToObj(i -> series("key" + i, true)).toList();
        when(mapper.listSeries(query, true)).thenReturn(candidates);
        var result = service.aggregate(query, true);
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getSeries()).hasSize(10).allSatisfy(s -> assertThat(s.getPoints()).hasSize(31));
        verify(mapper).dailyPoints(query, true, IntStream.range(0, 10).mapToObj(i -> "key" + i).toList());
    }

    @Test
    void emptyScopeDoesNotInventSeriesOrRunEmptyInQuery() {
        var result = service.aggregate(query(), true);
        assertThat(result.getSeries()).isEmpty();
        verify(mapper, never()).dailyPoints(any(), anyBoolean(), any());
    }

    @Test
    void repeatedQueriesDoNotMutateCachedMapperDimensionsOrMultiplyTotals() {
        var query = query();
        var cached = series("cached", true);
        when(mapper.listSeries(query, true)).thenReturn(List.of(cached));
        when(mapper.dailyPoints(query, true, List.of("cached"))).thenReturn(List.of(
                point("cached", query.getEndDate(), "300", "3", 1, 1, 1, 0)));
        var first = service.aggregate(query, true);
        var second = service.aggregate(query, true);
        assertThat(second).usingRecursiveComparison().isEqualTo(first);
        assertThat(cached.getQuantity()).isNull();
        assertThat(second.getSeries().get(0).getQuantity()).isEqualByComparingTo("300");
    }

    @Test
    void validatesDateAndKnownDepartmentBeforeQueryAndTrimsFilters() {
        var query = query();
        query.setEndDate(query.getStartDate().plusDays(31));
        assertThatThrownBy(() -> service.aggregate(query, true)).isInstanceOf(BusinessException.class);
        query.setEndDate(query.getStartDate());
        query.setDepartmentName("不存在的车间");
        assertThatThrownBy(() -> service.aggregate(query, true)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(mapper);
        query.setDepartmentName(" 多晶车间 ");
        query.setItemQuery(" 黄冰糖 ");
        service.aggregate(query, true);
        assertThat(query.getDepartmentName()).isEqualTo("多晶车间");
        assertThat(query.getItemQuery()).isEqualTo("黄冰糖");
    }

    @Test
    void sqlBindsFiltersAndKeepsIdentityUnitStageAndSavedValues() {
        var config = new Configuration();
        config.addMapper(ProductionDailyAggregateMapper.class);
        var query = query();
        query.setItemQuery("' OR 1=1 --");
        query.setDepartmentName("多晶车间");
        var prefix = ProductionDailyAggregateMapper.class.getName() + ".";
        var sql = config.getMappedStatement(prefix + "listSeries").getBoundSql(
                Map.of("query", query, "productOnly", true)).getSql();
        assertThat(sql).contains("JSON_ARRAY", "s.department_code", "s.department_name_snapshot", "v.category_code",
                "v.category_name_snapshot", "v.product_status_snapshot", "v.unit", "LIMIT 11", "AND 1=0");
        assertThat(sql).doesNotContain(query.getItemQuery(), "LOCATE(?,v.metric_name_snapshot)", "production_order", "month_tons", "year_tons");
        var daily = config.getMappedStatement(prefix + "dailyPoints").getBoundSql(
                Map.of("query", query, "productOnly", false, "keys", List.of("safe-key"))).getSql();
        assertThat(daily).contains("SUM(quantity)", "SUM(tons)", "COUNT(quantity)", "COUNT(tons)", "GROUP BY series_key,report_date",
                "LOCATE(?,v.metric_name_snapshot)");
        assertThat(daily).doesNotContain("COALESCE", "LIMIT", "safe-key", "SUM(v.month", "SUM(v.year", "AVG(", "_RATE'");
    }

    private static ProductionDailyReportAgentQueryDTO query() {
        var query = new ProductionDailyReportAgentQueryDTO();
        query.setStartDate(LocalDate.of(2026, 9, 5)); query.setEndDate(query.getStartDate().plusDays(2));
        return query;
    }
    private static ProductionDailyAggregateVO.Series series(String key, boolean tons) {
        var series = new ProductionDailyAggregateVO.Series();
        series.setSeriesKey(key); series.setTonnageApplicable(tons); return series;
    }
    private static ProductionDailyAggregateVO.Point point(String key, LocalDate day, String quantity, String tons,
                                                         int rows, int quantityRows, int tonnageRows, int drafts) {
        var point = new ProductionDailyAggregateVO.Point();
        point.setSeriesKey(key); point.setBusinessDate(day);
        point.setQuantity(quantity == null ? null : new BigDecimal(quantity));
        point.setConvertedTons(tons == null ? null : new BigDecimal(tons));
        point.setRecordCount(rows); point.setQuantityRows(quantityRows);
        point.setTonnageRows(tonnageRows); point.setDraftRows(drafts); return point;
    }
}
