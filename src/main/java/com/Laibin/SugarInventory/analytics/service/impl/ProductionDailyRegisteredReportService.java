package com.Laibin.SugarInventory.analytics.service.impl;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.service.ProductionDailyAggregateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionDailyRegisteredReportService {
    public static final String REPORT_ID = "production_daily_report_analysis_v1";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final ProductionDailyAggregateService aggregateService;

    public RegisteredReportRunVO run(RegisteredReportRunQueryDTO query) {
        if (query == null || !REPORT_ID.equals(query.getReportDefinitionId())
                || !Integer.valueOf(1).equals(query.getReportVersion())
                || query.getMetricKey() != null || query.getTaskType() != null) {
            throw new BusinessException(400, "日报趋势参数无效；不支持化验或任务筛选");
        }
        var scope = new ProductionDailyReportAgentQueryDTO();
        scope.setStartDate(query.getStartDate());
        scope.setEndDate(query.getEndDate());
        scope.setDepartmentName(query.getDepartmentName());
        scope.setItemQuery(query.getProductQuery());
        ProductionDailyAggregateService.validate(scope);
        if (scope.getEndDate().isAfter(LocalDate.now(BUSINESS_ZONE))) {
            throw new BusinessException(400, "日报趋势不能查询未来日期");
        }
        RegisteredReportComparisonAssembler.validateDailyComparison(query);
        var aggregate = aggregateService.aggregate(scope, true);
        List<String> notes = new ArrayList<>();
        if (aggregate.getSeries().isEmpty()) notes.add("所选范围没有可汇总的日报产量或能耗记录，不表示产量为0。");
        if (aggregate.isTruncated()) notes.add("仅展示前10个统计系列，请缩小车间或产品范围；导出与当前快照范围一致。");
        for (var series : aggregate.getSeries()) {
            boolean incomplete = series.getObservedDays() < aggregate.getRequestedDays()
                    || series.getQuantityRows() < series.getRecordCount()
                    || (series.isTonnageApplicable() && (series.getTonnageObservedDays() < aggregate.getRequestedDays()
                        || series.getTonnageRows() < series.getRecordCount()));
            if (incomplete || series.getDraftRows() > 0) notes.add(series.getDepartmentName() + " · "
                    + series.getLabel() + "：" + series.getCoverageLabel());
        }
        String dateLabel = scope.getStartDate().equals(scope.getEndDate()) ? scope.getStartDate().toString()
                : scope.getStartDate() + " 至 " + scope.getEndDate();
        var filters = new LinkedHashMap<String, String>();
        filters.put("businessDate", dateLabel);
        filters.put("sourceLabel", "生产日报");
        filters.put("departmentScope", scope.getDepartmentName() == null ? "全部车间及全厂汇总" : scope.getDepartmentName());
        filters.put("productScope", scope.getItemQuery() == null ? "全部产品" : "产品名称包含“" + scope.getItemQuery() + "”");
        return RegisteredReportRunVO.builder()
                .dataScope("SAVED_PRODUCTION_DAILY_REPORT")
                .reportRunId("report_run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .reportDefinitionId(REPORT_ID).reportVersion(1).reportName("生产日报分析")
                .metricDefinitionVersion("saved_daily_actual_and_tonnage_v1")
                .startDate(scope.getStartDate()).endDate(scope.getEndDate()).dateRangeLabel(dateLabel)
                .dataAsOf(LocalDateTime.now(BUSINESS_ZONE)).filtersApplied(filters)
                .seriesGranularity("DAY").productionDailyReport(aggregate)
                .dataQuality(RegisteredReportDataQualityVO.builder().partial(!notes.isEmpty()).notes(List.copyOf(notes)).build())
                .limitations(List.of(
                    "来源为车间保存的生产日报，包含草稿；不与生产订单登记产出相加。",
                    "仅汇总当日数量和已保存折吨，按日报业务日期统计；不重新换算，不累计月年值，不合计或平均产率。",
                    "全厂产量、车间产量及产品分类分别展示；不同环节和单位不得相加。分类只代表已记录产品行。",
                    "缺日或空值保留为空，记录为0的值保留为0；数量与折吨分别标明记录行数和覆盖天数。",
                    "全厂能耗保持原单位，不计算折吨、吨耗、预测或因果结论。",
                    "历史查看和导出均使用本次生成的快照；日报修改后需要重新生成新报表。"))
                .build();
    }
}
