package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyAggregateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionDailyAggregateService {
    public static final int MAX_SERIES = 10;
    private final ProductionDailyAggregateMapper mapper;

    /** productOnly controls the meaning of the name filter, not the unfiltered fact scope. */
    public ProductionDailyAggregateVO aggregate(ProductionDailyReportAgentQueryDTO query, boolean productOnly) {
        validate(query);
        int days = (int) ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate()) + 1;
        var candidates = mapper.listSeries(query, productOnly);
        // MyBatis session cache can return the same dimension instances on repeated queries.
        // Never accumulate into mapper-owned objects or later reads could multiply totals.
        var series = candidates.stream().limit(MAX_SERIES).map(candidate -> {
            var item = new ProductionDailyAggregateVO.Series();
            item.setSeriesKey(candidate.getSeriesKey());
            item.setSummaryType(candidate.getSummaryType());
            item.setDepartmentName(candidate.getDepartmentName());
            item.setLabel(candidate.getLabel());
            item.setUnit(candidate.getUnit());
            item.setTonnageApplicable(candidate.isTonnageApplicable());
            return item;
        }).toList();
        Map<String, Map<LocalDate, ProductionDailyAggregateVO.Point>> bySeries = new HashMap<>();
        if (!series.isEmpty()) {
            for (var point : mapper.dailyPoints(query, productOnly,
                    series.stream().map(ProductionDailyAggregateVO.Series::getSeriesKey).toList())) {
                bySeries.computeIfAbsent(point.getSeriesKey(), ignored -> new HashMap<>())
                        .put(point.getBusinessDate(), point);
            }
        }
        for (var item : series) {
            var saved = bySeries.getOrDefault(item.getSeriesKey(), Map.of());
            List<ProductionDailyAggregateVO.Point> points = new ArrayList<>();
            for (LocalDate date = query.getStartDate(); !date.isAfter(query.getEndDate()); date = date.plusDays(1)) {
                var point = saved.get(date);
                if (point == null) {
                    point = new ProductionDailyAggregateVO.Point();
                    point.setSeriesKey(item.getSeriesKey());
                    point.setBusinessDate(date);
                    // Deliberately leave both values null: no row is not recorded zero.
                }
                points.add(point);
                item.setQuantity(addNullable(item.getQuantity(), point.getQuantity()));
                item.setConvertedTons(addNullable(item.getConvertedTons(), point.getConvertedTons()));
                item.setRecordCount(item.getRecordCount() + point.getRecordCount());
                item.setQuantityRows(item.getQuantityRows() + point.getQuantityRows());
                item.setTonnageRows(item.getTonnageRows() + point.getTonnageRows());
                item.setDraftRows(item.getDraftRows() + point.getDraftRows());
                if (point.getQuantityRows() > 0) item.setObservedDays(item.getObservedDays() + 1);
                if (point.getTonnageRows() > 0) item.setTonnageObservedDays(item.getTonnageObservedDays() + 1);
            }
            item.setPoints(List.copyOf(points));
            String coverage = "数量已记录" + item.getQuantityRows() + "/" + item.getRecordCount()
                    + "行，覆盖" + item.getObservedDays() + "/" + days + "天";
            if (item.isTonnageApplicable()) {
                coverage += "；折吨已记录" + item.getTonnageRows() + "/" + item.getRecordCount()
                        + "行，覆盖" + item.getTonnageObservedDays() + "/" + days + "天";
            }
            if (item.getDraftRows() > 0) coverage += "；含" + item.getDraftRows() + "行未提交车间数据";
            item.setCoverageLabel(coverage);
        }
        var result = new ProductionDailyAggregateVO();
        result.setRequestedDays(days);
        result.setTruncated(candidates.size() > MAX_SERIES);
        result.setSeries(series);
        return result;
    }

    public static void validate(ProductionDailyReportAgentQueryDTO query) {
        if (query == null || query.getStartDate() == null || query.getEndDate() == null
                || query.getEndDate().isBefore(query.getStartDate())
                || ChronoUnit.DAYS.between(query.getStartDate(), query.getEndDate()) >= 31) invalid();
        query.setDepartmentName(trim(query.getDepartmentName()));
        query.setItemQuery(trim(query.getItemQuery()));
        if (query.getItemQuery() != null && query.getItemQuery().length() > 100) invalid();
        if (query.getDepartmentName() != null && ProductionDailyReportCatalog.sections().stream()
                .noneMatch(section -> section.name().equals(query.getDepartmentName()))) invalid();
    }

    private static void invalid() { throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_QUERY_INVALID); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        return right == null ? left : left == null ? right : left.add(right);
    }
}
