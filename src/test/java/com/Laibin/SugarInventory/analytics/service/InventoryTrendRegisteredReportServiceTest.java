package com.Laibin.SugarInventory.analytics.service;

import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayAnchorCheckRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayBalanceRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryReplayMovementRowVO;
import com.Laibin.SugarInventory.analytics.domain.vo.InventoryTrustedTrendPointRowVO;
import com.Laibin.SugarInventory.analytics.mapper.InventoryTrendReportMapper;
import com.Laibin.SugarInventory.analytics.service.impl.InventoryTrendRegisteredReportService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.inventoryhistory.domain.InventoryTrendReleaseGate;
import com.Laibin.SugarInventory.inventoryhistory.mapper.InventoryTrendReleaseGateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryTrendRegisteredReportServiceTest {
    private InventoryTrendReportMapper reportMapper;
    private InventoryTrendReleaseGateMapper gateMapper;

    @BeforeEach
    void setUp() {
        reportMapper = mock(InventoryTrendReportMapper.class);
        gateMapper = mock(InventoryTrendReleaseGateMapper.class);
    }

    @Test
    void replaysExistingSevenDayWindowWithoutWritingOfficialSnapshots() {
        LocalDate start = LocalDate.of(2026, 7, 14);
        LocalDate end = LocalDate.of(2026, 7, 20);
        String product = "黄冰糖（袋）";
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND"))
                .thenReturn(blockedGate());
        when(reportMapper.listReplayAnchorChecks(product))
                .thenReturn(List.of(anchor(product, 790, 790, "19750", "19750")));
        when(reportMapper.listCurrentProductBalances(product))
                .thenReturn(List.of(balance(product, 790, "19750")));
        when(reportMapper.listReplayMovementsAfter(start, product))
                .thenReturn(List.of(movement(end, product, 240, "6000")));

        var service = new InventoryTrendRegisteredReportService(reportMapper, gateMapper, true);
        var report = service.run(query(start, end, product));

        assertThat(report.getReportDefinitionId()).isEqualTo("inventory_level_trend_v1");
        assertThat(report.getDataScope()).isEqualTo("HISTORICAL_REPLAY_SIMULATION");
        assertThat(report.getInventoryTrendDailySeries()).hasSize(7);
        assertThat(report.getInventoryTrendDailySeries().get(0).getTotalPieces()).isEqualTo(550);
        assertThat(report.getInventoryTrendDailySeries().get(5).getTotalPieces()).isEqualTo(550);
        assertThat(report.getInventoryTrendDailySeries().get(6).getTotalPieces()).isEqualTo(790);
        assertThat(report.getInventoryTrendDailySeries().get(6).getPieceChange()).isEqualTo(240);
        assertThat(report.getInventoryTrendMetrics().getOpeningWeightKg())
                .isEqualByComparingTo("13750");
        assertThat(report.getInventoryTrendMetrics().getClosingWeightKg())
                .isEqualByComparingTo("19750");
        assertThat(report.getInventoryTrendMetrics().getNetChangePieces()).isEqualTo(240);
        assertThat(report.getDataQuality().getSimulationData()).isTrue();
        assertThat(report.getDataQuality().getReplayAnchorReconciled()).isTrue();
        assertThat(report.getLimitations()).anyMatch(value -> value.contains("不是正式日终快照"));
    }

    @Test
    void rejectsReplayWhenLegacyMovementsDoNotReconcileToCurrentInventory() {
        LocalDate start = LocalDate.of(2026, 7, 14);
        LocalDate end = LocalDate.of(2026, 7, 20);
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND"))
                .thenReturn(blockedGate());
        when(reportMapper.listReplayAnchorChecks(null))
                .thenReturn(List.of(anchor("黄冰糖（袋）", 790, 780, "19750", "19500")));

        var service = new InventoryTrendRegisteredReportService(reportMapper, gateMapper, true);

        assertThatThrownBy(() -> service.run(query(start, end, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不守恒");
    }

    @Test
    void keepsFirstDayMovementEvidenceWithoutChangingItsEndOfDayBalance() {
        LocalDate start = LocalDate.of(2026, 7, 14);
        LocalDate end = LocalDate.of(2026, 7, 20);
        String product = "黄冰糖（袋）";
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND"))
                .thenReturn(blockedGate());
        when(reportMapper.listReplayAnchorChecks(product))
                .thenReturn(List.of(anchor(product, 800, 800, "20000", "20000")));
        when(reportMapper.listCurrentProductBalances(product))
                .thenReturn(List.of(balance(product, 800, "20000")));
        when(reportMapper.listReplayMovementsAfter(start, product))
                .thenReturn(List.of(
                        movement(start, product, 10, "250"),
                        movement(end, product, 240, "6000")
                ));

        var service = new InventoryTrendRegisteredReportService(reportMapper, gateMapper, true);
        var report = service.run(query(start, end, product));

        assertThat(report.getInventoryTrendDailySeries().get(0).getTotalPieces()).isEqualTo(560);
        assertThat(report.getInventoryTrendDailySeries().get(0).getMovementRecordCount()).isEqualTo(6);
        assertThat(report.getInventoryTrendDailySeries().get(6).getTotalPieces()).isEqualTo(800);
    }

    @Test
    void keepsProductionPathBlockedUntilTrustedGatePasses() {
        LocalDate start = LocalDate.of(2026, 7, 14);
        LocalDate end = LocalDate.of(2026, 7, 20);
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND"))
                .thenReturn(blockedGate());

        var service = new InventoryTrendRegisteredReportService(reportMapper, gateMapper, false);

        assertThatThrownBy(() -> service.run(query(start, end, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("0/7 天");
    }

    @Test
    void usesPassedDailySnapshotsWhenTrustedWindowContainsQuery() {
        LocalDate start = LocalDate.of(2026, 7, 14);
        LocalDate end = LocalDate.of(2026, 7, 15);
        InventoryTrendReleaseGate gate = blockedGate();
        gate.setStatus("ELIGIBLE");
        gate.setConsecutivePassedDays(7);
        gate.setWindowStartDate(start);
        gate.setWindowEndDate(LocalDate.of(2026, 7, 20));
        when(gateMapper.findByGateKey("INVENTORY_LEVEL_TREND")).thenReturn(gate);
        when(reportMapper.listTrustedTrendPoints(start, end, null)).thenReturn(List.of(
                trusted(start, 550, "13750"),
                trusted(end, 590, "14750")
        ));

        var service = new InventoryTrendRegisteredReportService(reportMapper, gateMapper, false);
        var report = service.run(query(start, end, null));

        assertThat(report.getDataScope()).isEqualTo("TRUSTED_DAILY_CLOSE_SNAPSHOT");
        assertThat(report.getInventoryTrendMetrics().getNetChangePieces()).isEqualTo(40);
        assertThat(report.getDataQuality().getSimulationData()).isFalse();
        assertThat(report.getDataQuality().getTrustedSnapshotDayCount()).isEqualTo(2);
    }

    private static RegisteredReportRunQueryDTO query(
            LocalDate start,
            LocalDate end,
            String product) {
        RegisteredReportRunQueryDTO query = new RegisteredReportRunQueryDTO();
        query.setReportDefinitionId(InventoryTrendRegisteredReportService.REPORT_ID);
        query.setReportVersion(1);
        query.setStartDate(start);
        query.setEndDate(end);
        query.setProductQuery(product);
        return query;
    }

    private static InventoryTrendReleaseGate blockedGate() {
        InventoryTrendReleaseGate gate = new InventoryTrendReleaseGate();
        gate.setGateKey("INVENTORY_LEVEL_TREND");
        gate.setStatus("BLOCKED");
        gate.setRequiredPassedDays(7);
        gate.setConsecutivePassedDays(0);
        return gate;
    }

    private static InventoryReplayAnchorCheckRowVO anchor(
            String product,
            long expectedPieces,
            long currentPieces,
            String expectedWeight,
            String currentWeight) {
        InventoryReplayAnchorCheckRowVO row = new InventoryReplayAnchorCheckRowVO();
        row.setProductId(84);
        row.setProductName(product);
        row.setExpectedPieces(expectedPieces);
        row.setCurrentPieces(currentPieces);
        row.setPieceDifference(expectedPieces - currentPieces);
        row.setExpectedWeightKg(new BigDecimal(expectedWeight));
        row.setCurrentWeightKg(new BigDecimal(currentWeight));
        row.setWeightDifferenceKg(new BigDecimal(expectedWeight).subtract(new BigDecimal(currentWeight)));
        return row;
    }

    private static InventoryReplayBalanceRowVO balance(
            String product,
            long pieces,
            String weight) {
        InventoryReplayBalanceRowVO row = new InventoryReplayBalanceRowVO();
        row.setProductId(84);
        row.setProductName(product);
        row.setTotalPieces(pieces);
        row.setTotalWeightKg(new BigDecimal(weight));
        return row;
    }

    private static InventoryReplayMovementRowVO movement(
            LocalDate date,
            String product,
            long inboundPieces,
            String inboundWeight) {
        InventoryReplayMovementRowVO row = new InventoryReplayMovementRowVO();
        row.setBusinessDate(date);
        row.setProductId(84);
        row.setProductName(product);
        row.setInboundPieces(inboundPieces);
        row.setInboundWeightKg(new BigDecimal(inboundWeight));
        row.setOutboundWeightKg(BigDecimal.ZERO);
        row.setMovementRecordCount(6);
        row.setLatestRecordedAt(LocalDateTime.of(date, java.time.LocalTime.NOON));
        return row;
    }

    private static InventoryTrustedTrendPointRowVO trusted(
            LocalDate date,
            long pieces,
            String weight) {
        InventoryTrustedTrendPointRowVO row = new InventoryTrustedTrendPointRowVO();
        row.setBusinessDate(date);
        row.setProductId(84);
        row.setProductName("黄冰糖（袋）");
        row.setTotalPieces(pieces);
        row.setTotalWeightKg(new BigDecimal(weight));
        row.setDataAsOf(LocalDateTime.of(date.plusDays(1), java.time.LocalTime.MIDNIGHT));
        return row;
    }
}
