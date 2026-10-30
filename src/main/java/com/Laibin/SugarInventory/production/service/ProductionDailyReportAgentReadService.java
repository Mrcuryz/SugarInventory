package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportAgentQueryDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportAgentVO;
import com.Laibin.SugarInventory.production.mapper.ProductionDailyReportAgentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductionDailyReportAgentReadService {
    private final ProductionDailyReportAgentMapper mapper;
    private final ProductionDailyAggregateService aggregateService;

    @PreAuthorize("hasAuthority('production:daily-report:view')")
    @Transactional(readOnly = true)
    public ProductionDailyReportAgentVO query(ProductionDailyReportAgentQueryDTO query) {
        ProductionDailyAggregateService.validate(query);
        query.setPage(query.getPage() == null ? 1 : query.getPage());
        query.setSize(query.getSize() == null ? 20 : query.getSize());
        if (query.getPage() < 1 || query.getPage() > 10000 || query.getSize() < 1 || query.getSize() > 50) {
            throw new BusinessException(ErrorCode.PRODUCTION_DAILY_REPORT_QUERY_INVALID);
        }
        ProductionDailyReportAgentVO result = new ProductionDailyReportAgentVO();
        result.setDateRangeLabel(query.getStartDate().equals(query.getEndDate())
                ? query.getStartDate().toString() : query.getStartDate() + " 至 " + query.getEndDate());
        result.setScopeLabel((query.getDepartmentName() == null ? "全部车间及全厂汇总" : query.getDepartmentName())
                + (query.getItemQuery() == null ? "" : "；名称包含“" + query.getItemQuery() + "”"));
        result.setReports(mapper.listReports(query));
        result.setTotal(mapper.countRows(query));
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        int offset = (query.getPage() - 1) * query.getSize();
        result.setRecords(mapper.pageRows(query, offset));
        result.setHasMore((long) offset + result.getRecords().size() < result.getTotal());
        result.setEmpty(result.getReports().isEmpty());
        var aggregate = aggregateService.aggregate(query, false);
        result.setRequestedDays(aggregate.getRequestedDays());
        result.setOverviewTruncated(aggregate.isTruncated());
        // Keep the existing overview payload small; trend points belong to report snapshots only.
        result.setOverview(aggregate.getSeries().stream().map(item -> {
            var overview = new ProductionDailyReportAgentVO.Overview();
            org.springframework.beans.BeanUtils.copyProperties(item, overview);
            return overview;
        }).toList());
        result.setLimitations(List.of(
                "根据生产日报统计，不与订单登记产出相加；草稿车间数据包含在内，提交状态另行标注。",
                "日报日期列表覆盖所选日期范围；明细另按车间和名称筛选、分页，不代表全部明细。",
                "草稿与已提交记录均展示；分车间提交状态与整份日报状态分别保留。",
                "空值表示未填报，不等于0；折吨及月/年累计均为已保存填报值，不重新换算或累加。",
                "概况按完整日期和筛选范围统计，与明细页码无关；仅加总当日数量和已保存折吨，不相加月年累计，不求和或平均产率。",
                "全厂产量直接读取总产量指标；车间产量、产品分类与全厂总量并列展示，不相加。分类仅代表已记录行，不保证覆盖该车间全部产品。",
                "数量或折吨缺行、缺日时只代表已记录部分；全空为未记录而非0。日报草稿状态不构成名称无匹配的原因。"));
        return result;
    }

}
