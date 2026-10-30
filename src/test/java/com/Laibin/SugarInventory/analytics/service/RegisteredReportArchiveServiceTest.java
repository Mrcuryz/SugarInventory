package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportExportAuditPO;
import com.Laibin.SugarInventory.analytics.domain.po.RegisteredReportRunPO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportDataQualityVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportComparisonVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricComparisonVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportCurrentInventoryMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportOperationsOverviewVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleDailyPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskCycleTypeBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportPalletTaskPendingItemVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowDailyPointVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportProductionFlowOrderBreakdownVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportQualityMetricsVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportExportAuditMapper;
import com.Laibin.SugarInventory.analytics.mapper.RegisteredReportRunMapper;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportArchiveService;
import com.Laibin.SugarInventory.analytics.service.impl.PalletTaskCycleRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.ProductionInputOutputRegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportServiceImpl;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportXlsxExporter;
import com.Laibin.SugarInventory.common.BusinessException;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisteredReportArchiveServiceTest {
    private RegisteredReportRunMapper reportRunMapper;
    private RegisteredReportExportAuditMapper exportAuditMapper;
    private ObjectMapper objectMapper;
    private RegisteredReportArchiveService service;

    @BeforeEach
    void setUp() {
        reportRunMapper = mock(RegisteredReportRunMapper.class);
        exportAuditMapper = mock(RegisteredReportExportAuditMapper.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new RegisteredReportArchiveService(
                reportRunMapper,
                exportAuditMapper,
                objectMapper,
                new RegisteredReportXlsxExporter(objectMapper));
    }

    @Test
    void persistsImmutableSnapshotWithOwnerPermissionAndContentHash() {
        RegisteredReportRunVO report = report();

        service.persist(report, 7, "陈思聪");

        ArgumentCaptor<RegisteredReportRunPO> captor =
                ArgumentCaptor.forClass(RegisteredReportRunPO.class);
        verify(reportRunMapper).insert(captor.capture());
        RegisteredReportRunPO stored = captor.getValue();
        assertThat(stored.getReportRunId()).isEqualTo(report.getReportRunId());
        assertThat(stored.getOwnerUserId()).isEqualTo(7);
        assertThat(stored.getOwnerDisplayName()).isEqualTo("陈思聪");
        assertThat(stored.getRequiredPermission()).isEqualTo("production:order:view");
        assertThat(stored.getContentSha256()).hasSize(64);
        assertThat(stored.getPayloadJson()).contains("生产登记产出日报");
        assertThat(stored.getExpiresAt()).isAfter(stored.getGeneratedAt());
    }

    @Test
    void loadsOnlyWhenOwnerPermissionExpiryAndHashAreValid() throws Exception {
        RegisteredReportRunVO report = report();
        RegisteredReportRunPO stored = stored(report);
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(stored);

        RegisteredReportRunVO loaded = service.load(
                report.getReportRunId(),
                7,
                Set.of("production:order:view"));

        assertThat(loaded.getReportRunId()).isEqualTo(report.getReportRunId());
        assertThat(loaded.getMetrics().getTotalWeightKg()).isEqualByComparingTo("3075.00");

        assertThatThrownBy(() -> service.load(
                report.getReportRunId(),
                7,
                Set.of("assay:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有查看或导出");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listsOnlyCurrentOwnersVisibleUnexpiredSnapshotsAsSafeSummaries() throws Exception {
        RegisteredReportRunPO stored = stored(report());
        Page<RegisteredReportRunPO> storedPage = new Page<>(1, 10, 1);
        storedPage.setRecords(List.of(stored));
        when(reportRunMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(storedPage);

        var history = service.list(
                7,
                Set.of("production:order:view"),
                1,
                10,
                RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID);

        assertThat(history.getTotal()).isEqualTo(1);
        assertThat(history.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getReportRunId()).isEqualTo(stored.getReportRunId());
            assertThat(item.getReportName()).isEqualTo("生产登记产出日报");
            assertThat(item.getScopeLabel()).contains("全部产品");
            assertThat(item.isComparisonIncluded()).isTrue();
            assertThat(item.isPartialData()).isFalse();
            assertThat(item.getExpiresAt()).isEqualTo(stored.getExpiresAt());
        });
    }

    @Test
    void returnsEmptyHistoryWhenCurrentUserHasNoReportPermissionSet() {
        var history = service.list(7, Set.of("warehouse:view"), 1, 10, null);

        assertThat(history.getTotal()).isZero();
        assertThat(history.getRecords()).isEmpty();
    }

    @Test
    void rejectsInvalidHistoryPaginationAndReportDefinition() {
        assertThatThrownBy(() -> service.list(
                7,
                Set.of("production:order:view"),
                0,
                10,
                null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分页参数无效");

        assertThatThrownBy(() -> service.list(
                7,
                Set.of("production:order:view"),
                1,
                10,
                "unknown_report"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表类型无效");
    }

    @Test
    void rejectsExpiredOrTamperedSnapshots() throws Exception {
        RegisteredReportRunPO expired = stored(report());
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(expired);

        assertThatThrownBy(() -> service.load(
                expired.getReportRunId(),
                7,
                Set.of("production:order:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");

        RegisteredReportRunPO tampered = stored(report());
        tampered.setPayloadJson(tampered.getPayloadJson().replace("3075.00", "9999.00"));
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(tampered);

        assertThatThrownBy(() -> service.load(
                tampered.getReportRunId(),
                7,
                Set.of("production:order:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容校验失败");
    }

    @Test
    void exportsXlsxFromStoredSnapshotAndWritesAuditRecord() throws Exception {
        RegisteredReportRunPO stored = stored(report());
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(stored);

        RegisteredReportArchiveService.ExportedReportFile exported =
                service.exportXlsx(
                        stored.getReportRunId(),
                        7,
                        "陈思聪",
                        Set.of("production:order:view"));

        assertThat(exported.filename()).contains("生产登记产出日报").endsWith(".xlsx");
        assertThat(exported.auditRef()).startsWith("report_export_");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(exported.content()))) {
            assertThat(workbook.getSheet("报表说明")).isNotNull();
            assertThat(workbook.getSheet("核心指标")).isNotNull();
            assertThat(workbook.getSheet("跨期比较")).isNotNull();
            assertThat(workbook.getSheet("口径与限制")).isNotNull();
            assertThat(workbook.getSheet("报表说明").getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("生产登记产出日报");
        }

        ArgumentCaptor<RegisteredReportExportAuditPO> captor =
                ArgumentCaptor.forClass(RegisteredReportExportAuditPO.class);
        verify(exportAuditMapper).insert(captor.capture());
        assertThat(captor.getValue().getReportRunId()).isEqualTo(stored.getReportRunId());
        assertThat(captor.getValue().getContentSha256()).hasSize(64);
    }

    @Test
    void productionFlowSnapshotRequiresBothPermissionsAndExportsRegisteredSections()
            throws Exception {
        RegisteredReportRunPO stored = stored(productionFlowReport());
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(stored);

        assertThat(stored.getRequiredPermission())
                .isEqualTo("production:order:view,production:material:view");
        assertThatThrownBy(() -> service.load(
                stored.getReportRunId(),
                7,
                Set.of("production:order:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有查看或导出");

        RegisteredReportArchiveService.ExportedReportFile exported =
                service.exportXlsx(
                        stored.getReportRunId(),
                        7,
                        "陈思聪",
                        Set.of("production:order:view", "production:material:view"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(exported.content()))) {
            assertThat(workbook.getSheet("核心指标")).isNotNull();
            assertThat(workbook.getSheet("每日独立序列")).isNotNull();
            assertThat(workbook.getSheet("订单归属事实")).isNotNull();
            assertThat(workbook.getSheet("口径与限制")).isNotNull();
        }
    }

    @Test
    void palletTaskCycleSnapshotRequiresTaskPermissionAndExportsRegisteredSections()
            throws Exception {
        RegisteredReportRunPO stored = stored(palletTaskCycleReport());
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(stored);

        assertThat(stored.getRequiredPermission()).isEqualTo("task:view");
        assertThatThrownBy(() -> service.load(
                stored.getReportRunId(),
                7,
                Set.of("production:order:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有查看或导出");

        RegisteredReportArchiveService.ExportedReportFile exported = service.exportXlsx(
                stored.getReportRunId(),
                7,
                "陈思聪",
                Set.of("task:view"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(exported.content()))) {
            assertThat(workbook.getSheet("核心指标")).isNotNull();
            assertThat(workbook.getSheet("按日任务周期")).isNotNull();
            assertThat(workbook.getSheet("任务类型分布")).isNotNull();
            assertThat(workbook.getSheet("进行中任务")).isNotNull();
            assertThat(workbook.getSheet("数据质量")).isNotNull();
            assertThat(workbook.getSheet("口径与限制")).isNotNull();
        }
    }

    @Test
    void todayOverviewRequiresCompositePermissionAndExportsFiveFactSections()
            throws Exception {
        RegisteredReportRunPO stored = stored(todayOperationsOverviewReport());
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(stored);

        assertThat(stored.getRequiredPermission()).isEqualTo(
                "production:order:view,production:material:view,assay:view,inventory:view,task:view");
        assertThatThrownBy(() -> service.load(
                stored.getReportRunId(),
                7,
                Set.of(
                        "production:order:view",
                        "production:material:view",
                        "assay:view",
                        "inventory:view")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有查看或导出");

        Set<String> permissions = Set.of(
                "production:order:view",
                "production:material:view",
                "assay:view",
                "inventory:view",
                "task:view");
        RegisteredReportArchiveService.ExportedReportFile exported = service.exportXlsx(
                stored.getReportRunId(), 7, "陈思聪", permissions);

        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(exported.content()))) {
            assertThat(workbook.getSheet("今日产出")).isNotNull();
            assertThat(workbook.getSheet("今日化验")).isNotNull();
            assertThat(workbook.getSheet("今日领用与产出")).isNotNull();
            assertThat(workbook.getSheet("当前库存")).isNotNull();
            assertThat(workbook.getSheet("今日托盘任务")).isNotNull();
            assertThat(workbook.getSheet("口径与限制")).isNotNull();
        }
    }

    private RegisteredReportRunPO stored(RegisteredReportRunVO report) throws Exception {
        service.persist(report, 7, "陈思聪");
        ArgumentCaptor<RegisteredReportRunPO> captor =
                ArgumentCaptor.forClass(RegisteredReportRunPO.class);
        verify(reportRunMapper, atLeastOnce()).insert(captor.capture());
        return captor.getAllValues().get(captor.getAllValues().size() - 1);
    }

    @Test
    void dailySnapshotRequiresCurrentViewAndSeparateExportPermissionAndPreservesNullZero() throws Exception {
        var aggregate = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO();
        aggregate.setRequestedDays(2);
        var series = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Series();
        series.setSeriesKey("test-series"); series.setLabel("总产量"); series.setSummaryType("全厂产量");
        series.setDepartmentName("全厂汇总"); series.setUnit("件"); series.setTonnageApplicable(true);
        series.setQuantity(java.math.BigDecimal.ZERO); series.setRecordCount(1); series.setQuantityRows(1);
        var missing = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Point();
        missing.setBusinessDate(LocalDate.of(2026, 9, 5));
        var zero = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Point();
        zero.setBusinessDate(LocalDate.of(2026, 9, 6)); zero.setQuantity(java.math.BigDecimal.ZERO); zero.setRecordCount(1);
        series.setPoints(List.of(missing, zero)); aggregate.setSeries(List.of(series));
        var report = report().toBuilder().reportDefinitionId(
                com.Laibin.SugarInventory.analytics.service.impl.ProductionDailyRegisteredReportService.REPORT_ID)
                .reportName("生产日报分析").dataScope("SAVED_PRODUCTION_DAILY_REPORT")
                .metrics(null).comparison(null).productionDailyReport(aggregate).build();
        var stored = stored(report);
        assertThat(stored.getRequiredPermission()).isEqualTo("production:daily-report:view");
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        // Altering live objects after persistence must not change history or export.
        series.setQuantity(new java.math.BigDecimal("999"));
        var loaded = service.load(report.getReportRunId(), 7, Set.of("production:daily-report:view"));
        assertThat(loaded.getProductionDailyReport().getSeries().get(0).getQuantity()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> service.exportXlsx(report.getReportRunId(), 7, "test", Set.of("production:daily-report:view")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("没有导出");
        org.mockito.Mockito.verifyNoInteractions(exportAuditMapper);
        assertThatThrownBy(() -> service.load(report.getReportRunId(), 7, Set.of("production:daily-report:export")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.load(report.getReportRunId(), 7, Set.of("production:order:view")))
                .isInstanceOf(BusinessException.class);
        var exported = service.exportXlsx(report.getReportRunId(), 7, "test",
                Set.of("production:daily-report:view", "production:daily-report:export"));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.content()))) {
            var summary = workbook.getSheet("日报汇总");
            assertThat(summary.getRow(1).getCell(4).getNumericCellValue()).isZero();
            var days = workbook.getSheet("日报按日趋势");
            assertThat(days.getRow(1).getCell(0).getStringCellValue()).isEqualTo("2026-09-05");
            assertThat(days.getRow(1).getCell(5).getStringCellValue()).isEmpty();
            assertThat(days.getRow(2).getCell(5).getNumericCellValue()).isZero();
            assertThat(days.getRow(2).getCell(6).getStringCellValue()).isEmpty();
            assertThat(workbook.getSheet("口径与限制")).isNotNull();
        }
        verify(exportAuditMapper).insert(any(RegisteredReportExportAuditPO.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void dailyHistoryUsesViewPermissionAndAcceptsItsDefinitionFilter() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "test"),
                RegisteredReportRunPO.class);
        var page = new Page<RegisteredReportRunPO>(1, 10, 0); page.setRecords(List.of());
        when(reportRunMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        service.list(7, Set.of("production:daily-report:view"), 1, 10,
                com.Laibin.SugarInventory.analytics.service.impl.ProductionDailyRegisteredReportService.REPORT_ID);
        var captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(reportRunMapper).selectPage(any(Page.class), captor.capture());
        var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) captor.getValue();
        wrapper.getSqlSegment();
        assertThat(wrapper.getParamNameValuePairs().values()).contains("production:daily-report:view", 7,
                "production_daily_report_analysis_v1").doesNotContain("production:daily-report:export");
    }

    @Test
    void dailyComparisonSurvivesSnapshotRoundtripAndExportsAlongsideDailySheets() throws Exception {
        var aggregate = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO();
        aggregate.setRequestedDays(1);
        var series = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Series();
        series.setLabel("全厂产量"); series.setUnit("件"); series.setQuantity(java.math.BigDecimal.ZERO);
        var point = new com.Laibin.SugarInventory.production.domain.vo.ProductionDailyAggregateVO.Point();
        point.setBusinessDate(LocalDate.of(2026, 9, 8)); point.setQuantity(java.math.BigDecimal.ZERO);
        series.setPoints(List.of(point)); aggregate.setSeries(List.of(series));
        var comparison = RegisteredReportComparisonVO.builder().comparisonMode("PREVIOUS_PERIOD")
                .comparisonLabel("上一等长期间").currentStartDate(LocalDate.of(2026, 9, 8))
                .currentEndDate(LocalDate.of(2026, 9, 8)).currentDateRangeLabel("2026-09-08")
                .comparisonStartDate(LocalDate.of(2026, 9, 7)).comparisonEndDate(LocalDate.of(2026, 9, 7))
                .comparisonDateRangeLabel("2026-09-07").currentPeriodDays(1).comparisonPeriodDays(1)
                .metrics(List.of(RegisteredReportMetricComparisonVO.builder().metricCode("daily_0")
                        .metricLabel("全厂产量（数量）").unit("件").currentValue(java.math.BigDecimal.ZERO)
                        .comparisonValue(new java.math.BigDecimal("20")).absoluteChange(new java.math.BigDecimal("-20"))
                        .percentChange(null).note("至少一期缺日，仅展示已记录值差额").build()))
                .notes(List.of("来源为已保存的生产日报，包含草稿")).build();
        var report = report().toBuilder().reportDefinitionId(
                com.Laibin.SugarInventory.analytics.service.impl.ProductionDailyRegisteredReportService.REPORT_ID)
                .reportName("生产日报分析").dataScope("SAVED_PRODUCTION_DAILY_REPORT")
                .metrics(null).comparison(comparison).productionDailyReport(aggregate).build();
        var saved = stored(report);
        when(reportRunMapper.selectOne(any(Wrapper.class))).thenReturn(saved);
        var restored = service.load(report.getReportRunId(), 7, Set.of("production:daily-report:view"));
        assertThat(restored.getComparison()).isEqualTo(comparison);
        var exported = service.exportXlsx(report.getReportRunId(), 7, "test",
                Set.of("production:daily-report:view", "production:daily-report:export"));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exported.content()))) {
            assertThat(workbook.getSheet("日报汇总")).isNotNull();
            assertThat(workbook.getSheet("日报按日趋势")).isNotNull();
            assertThat(workbook.getSheet("比较说明").getRow(1).getCell(0).getStringCellValue()).contains("包含草稿");
            var table = workbook.getSheet("跨期比较");
            assertThat(table).isNotNull();
            assertThat(table.getRow(1).getCell(2).getNumericCellValue()).isZero();
            assertThat(table.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(20);
            assertThat(table.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(-20);
            assertThat(table.getRow(1).getCell(5).getStringCellValue()).isEmpty();
            assertThat(table.getRow(1).getCell(10).getStringCellValue()).contains("缺日");
        }
    }

    private static RegisteredReportRunVO report() {
        LocalDate day = LocalDate.of(2026, 7, 27);
        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_PRODUCTION_OUTPUT")
                .reportRunId("report_run_0123456789abcdef")
                .reportDefinitionId(RegisteredReportServiceImpl.DAILY_PRODUCTION_REPORT_ID)
                .reportVersion(1)
                .reportName("生产登记产出日报")
                .metricDefinitionVersion("production_registered_output_v1")
                .startDate(day)
                .endDate(day)
                .dateRangeLabel(day.toString())
                .dataAsOf(LocalDateTime.of(2026, 7, 27, 18, 0))
                .filtersApplied(Map.of("businessDate", day.toString(), "productScope", "全部产品"))
                .metrics(RegisteredReportMetricsVO.builder()
                        .outputRecordCount(2)
                        .productionOrderCount(2)
                        .totalWeightKg(new java.math.BigDecimal("3075.00"))
                        .totalBoardCount(3)
                        .loosePieceCount(3)
                        .totalPieces(123)
                        .requiredQrCount(4)
                        .boundQrCount(3)
                        .inboundQrCount(1)
                        .build())
                .dailySeries(List.of())
                .productBreakdowns(List.of())
                .comparison(RegisteredReportComparisonVO.builder()
                        .comparisonMode("PREVIOUS_PERIOD")
                        .comparisonLabel("上一等长期间")
                        .currentStartDate(day)
                        .currentEndDate(day)
                        .currentDateRangeLabel(day.toString())
                        .comparisonStartDate(day.minusDays(1))
                        .comparisonEndDate(day.minusDays(1))
                        .comparisonDateRangeLabel(day.minusDays(1).toString())
                        .currentPeriodDays(1)
                        .comparisonPeriodDays(1)
                        .metrics(List.of(RegisteredReportMetricComparisonVO.builder()
                                .metricCode("totalWeightKg")
                                .metricLabel("已登记产出重量")
                                .unit("kg")
                                .additive(true)
                                .currentValue(new java.math.BigDecimal("3075"))
                                .comparisonValue(new java.math.BigDecimal("3000"))
                                .absoluteChange(new java.math.BigDecimal("75"))
                                .percentChange(new java.math.BigDecimal("2.5"))
                                .build()))
                        .notes(List.of("变化只描述登记数值差异。"))
                        .build())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(false)
                        .notes(List.of())
                        .build())
                .limitations(List.of("产量仅统计已登记且未取消的产出记录。"))
                .build();
    }

    private static RegisteredReportRunVO productionFlowReport() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 7, 27);
        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_PRODUCTION_INPUT_OUTPUT_FLOW")
                .reportRunId("report_run_fedcba9876543210")
                .reportDefinitionId(ProductionInputOutputRegisteredReportService.REPORT_ID)
                .reportVersion(1)
                .reportName("生产领料—登记产出趋势")
                .metricDefinitionVersion("production_input_output_independent_series_v1")
                .startDate(start)
                .endDate(end)
                .dateRangeLabel(start + " 至 " + end)
                .dataAsOf(LocalDateTime.of(2026, 7, 27, 18, 0))
                .filtersApplied(Map.of(
                        "businessDate", start + " 至 " + end,
                        "productScope", "全部产品"))
                .seriesGranularity("DAY")
                .productionFlowMetrics(RegisteredReportProductionFlowMetricsVO.builder()
                        .materialInputRecordCount(2)
                        .materialInputOrderCount(1)
                        .materialInputWeightKg(new java.math.BigDecimal("1656.8"))
                        .stableOutputRecordCount(3)
                        .stableOutputOrderCount(2)
                        .stableOutputWeightKg(new java.math.BigDecimal("1983.8"))
                        .cohortOrderCount(2)
                        .completedOrderCount(2)
                        .completedOrdersWithInputCount(1)
                        .completedOrdersMissingInputCount(1)
                        .completedOrdersWithStableOutputCount(2)
                        .completedOrdersMissingOutputCount(0)
                        .build())
                .productionFlowDailySeries(List.of(
                        RegisteredReportProductionFlowDailyPointVO.builder()
                                .businessDate(LocalDate.of(2026, 6, 30))
                                .materialInputRecordCount(2)
                                .materialInputWeightKg(new java.math.BigDecimal("1656.8"))
                                .stableOutputRecordCount(3)
                                .stableOutputWeightKg(new java.math.BigDecimal("1983.8"))
                                .build()))
                .productionFlowOrderBreakdowns(List.of(
                        RegisteredReportProductionFlowOrderBreakdownVO.builder()
                                .orderNo("PO202606300001")
                                .orderTypeLabel("成品生产")
                                .orderStatusLabel("已完成")
                                .productionDate(LocalDate.of(2026, 6, 30))
                                .inputSourceLabel("实际领料记录")
                                .inputRecordCount(2)
                                .inputWeightKg(new java.math.BigDecimal("1656.8"))
                                .stableOutputRecordCount(1)
                                .stableOutputWeightKg(new java.math.BigDecimal("327.0"))
                                .outputProductNames("黄冰糖（袋）")
                                .completenessLabel("输入和稳定产出均已登记")
                                .build()))
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(true)
                        .completedOrderCount(2)
                        .completedOrdersWithInputCount(1)
                        .completedOrdersMissingInputCount(1)
                        .completedOrdersWithStableOutputCount(2)
                        .completedOrdersMissingOutputCount(0)
                        .notes(List.of("1 个已完成订单缺少输入登记。"))
                        .build())
                .limitations(List.of(
                        "每日领料和每日产出采用不同业务日期，两条序列不能直接相除。"))
                .build();
    }

    private static RegisteredReportRunVO palletTaskCycleReport() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_PALLET_TASK_CYCLE_TIME")
                .reportRunId("report_run_abcdef0123456789")
                .reportDefinitionId(PalletTaskCycleRegisteredReportService.REPORT_ID)
                .reportVersion(1)
                .reportName("托盘任务处理耗时趋势")
                .metricDefinitionVersion("registered_pallet_task_cycle_v1")
                .startDate(start)
                .endDate(end)
                .dateRangeLabel(start + " 至 " + end)
                .dataAsOf(LocalDateTime.of(2026, 7, 31, 18, 0))
                .filtersApplied(Map.of(
                        "businessDate", "任务创建日期 " + start + " 至 " + end,
                        "productScope", "全部产品",
                        "taskScope", "成品入库任务"))
                .palletTaskCycleMetrics(RegisteredReportPalletTaskCycleMetricsVO.builder()
                        .cohortTaskCount(4)
                        .completedTaskCount(2)
                        .inProgressTaskCount(1)
                        .canceledTaskCount(1)
                        .medianDurationSeconds(120L)
                        .maximumWaitingSeconds(3600L)
                        .build())
                .palletTaskCycleDailySeries(List.of(
                        RegisteredReportPalletTaskCycleDailyPointVO.builder()
                                .businessDate(LocalDate.of(2026, 7, 30))
                                .taskCount(4)
                                .completedTaskCount(2)
                                .inProgressTaskCount(1)
                                .canceledTaskCount(1)
                                .medianDurationSeconds(120L)
                                .build()))
                .palletTaskCycleTypeBreakdowns(List.of(
                        RegisteredReportPalletTaskCycleTypeBreakdownVO.builder()
                                .taskTypeLabel("成品入库任务")
                                .taskCount(4)
                                .completedTaskCount(2)
                                .inProgressTaskCount(1)
                                .canceledTaskCount(1)
                                .medianDurationSeconds(120L)
                                .build()))
                .palletTaskPendingItems(List.of(
                        RegisteredReportPalletTaskPendingItemVO.builder()
                                .palletCode("BT001")
                                .taskTypeLabel("成品入库任务")
                                .productName("黄冰糖（袋）")
                                .createdAt(LocalDateTime.of(2026, 7, 30, 10, 0))
                                .waitingSeconds(3600L)
                                .targetWarehouseName("1号库位")
                                .build()))
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(true)
                        .tasksWithoutOperationBatchCount(4)
                        .notes(List.of("4 条任务没有操作批次号。"))
                        .build())
                .limitations(List.of("本报表不代表员工绩效。"))
                .build();
    }

    private static RegisteredReportRunVO todayOperationsOverviewReport() {
        LocalDate day = LocalDate.of(2026, 8, 3);
        return RegisteredReportRunVO.builder()
                .dataScope("REGISTERED_TODAY_OPERATIONS_SNAPSHOT")
                .reportRunId("report_run_13572468abcdef09")
                .reportDefinitionId(
                        RegisteredReportServiceImpl.TODAY_OPERATIONS_OVERVIEW_REPORT_ID)
                .reportVersion(1)
                .reportName("今日运营概览")
                .metricDefinitionVersion("today_registered_operations_snapshot_v1")
                .startDate(day)
                .endDate(day)
                .dateRangeLabel(day.toString())
                .dataAsOf(LocalDateTime.of(2026, 8, 3, 18, 0))
                .filtersApplied(Map.of(
                        "businessDate", day.toString(),
                        "productScope", "全部产品",
                        "inventoryScope", "当前全部在库库存",
                        "taskScope", "当日创建任务与当前待处理任务"))
                .operationsOverview(RegisteredReportOperationsOverviewVO.builder()
                        .businessDate(day)
                        .productionOutput(RegisteredReportMetricsVO.builder()
                                .outputRecordCount(1)
                                .productionOrderCount(1)
                                .totalWeightKg(new java.math.BigDecimal("1980"))
                                .totalPieces(80)
                                .build())
                        .assayQuality(RegisteredReportQualityMetricsVO.builder()
                                .assayRecordCount(3)
                                .judgedRecordCount(2)
                                .passCount(2)
                                .noStandardCount(1)
                                .passRatePercent(new java.math.BigDecimal("100"))
                                .build())
                        .productionFlow(RegisteredReportProductionFlowMetricsVO.builder()
                                .materialInputRecordCount(2)
                                .materialInputWeightKg(new java.math.BigDecimal("2000"))
                                .stableOutputRecordCount(1)
                                .stableOutputWeightKg(new java.math.BigDecimal("1980"))
                                .build())
                        .currentInventory(RegisteredReportCurrentInventoryMetricsVO.builder()
                                .productCount(5)
                                .warehouseCount(3)
                                .palletCount(12)
                                .totalEquivalentPieces(550)
                                .totalStockText("13 板 30 件")
                                .totalWeightText("13750 kg")
                                .build())
                        .todayPalletTasks(RegisteredReportPalletTaskCycleMetricsVO.builder()
                                .cohortTaskCount(4)
                                .completedTaskCount(2)
                                .inProgressTaskCount(1)
                                .canceledTaskCount(1)
                                .build())
                        .currentPendingTaskCount(7)
                        .build())
                .dataQuality(RegisteredReportDataQualityVO.builder()
                        .partial(false)
                        .notes(List.of())
                        .build())
                .limitations(List.of("今日事实与当前快照分开解释。"))
                .build();
    }
}
