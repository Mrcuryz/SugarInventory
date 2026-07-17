package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayReportDetailQueryDTO;
import com.Laibin.SugarInventory.domain.vo.AssayFailedMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayReportDetailVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardSnapshotVO;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardItemVO;
import com.Laibin.SugarInventory.service.impl.AssayReportDetailServiceImpl;
import com.Laibin.SugarInventory.service.support.AssayReportRefCodec;
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

class AssayReportDetailServiceImplTest {
    private AssayService assayService;
    private AssayReportRefCodec refCodec;
    private AssayReportDetailService service;

    @BeforeEach
    void setUp() {
        assayService = mock(AssayService.class);
        refCodec = new AssayReportRefCodec();
        service = new AssayReportDetailServiceImpl(assayService, refCodec);
    }

    @Test
    void returnsSafeReportDetailFromOpaqueRef() {
        AssayVO assay = failedAssay();
        String reportRef = refCodec.encode(1001, 84, LocalDate.of(2026, 7, 8));
        when(assayService.getAssayById(1001)).thenReturn(assay);

        AssayReportDetailQueryDTO query = new AssayReportDetailQueryDTO();
        query.setReportRef(reportRef);
        AssayReportDetailVO result = service.getReportDetail(query);

        assertThat(result.getReportRef()).isEqualTo(reportRef);
        assertThat(result.getProductLabel()).isEqualTo("黄冰糖");
        assertThat(result.getJudgeLabel()).isEqualTo("不合格");
        assertThat(result.getSummaryText()).contains("色值");
        assertThat(result.getMetrics()).hasSize(7);
        assertThat(result.getMetrics().get(0).getMetricName()).isEqualTo("色值");
        assertThat(result.getMetrics().get(0).getActualValueText()).isEqualTo("120");
        assertThat(result.getMetrics().get(0).getStandardRangeText()).isEqualTo("≤ 100");
        assertThat(result.getMetrics().get(0).getResultLabel()).isEqualTo("不合格");
        assertThat(result.toString()).doesNotContain("assayId", "productId");
    }

    @Test
    void rejectsTamperedRef() {
        String reportRef = refCodec.encode(1001, 84, LocalDate.of(2026, 7, 8));
        when(assayService.getAssayById(1001)).thenReturn(failedAssayWithProduct(85));
        AssayReportDetailQueryDTO query = new AssayReportDetailQueryDTO();
        query.setReportRef(reportRef);

        assertThatThrownBy(() -> service.getReportDetail(query))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不匹配");
    }

    private AssayVO failedAssay() {
        return failedAssayWithProduct(84);
    }

    private AssayVO failedAssayWithProduct(Integer productId) {
        AssayVO assay = new AssayVO();
        assay.setId(1001);
        assay.setProductId(productId);
        assay.setProductName("黄冰糖");
        assay.setSampleDate(LocalDate.of(2026, 7, 8));
        assay.setCreatedAt(LocalDateTime.of(2026, 7, 8, 10, 0));
        assay.setJudgeResult("FAIL");
        assay.setAppliedStandardName("黄冰糖标准");
        assay.setAppliedStandardVersion(3);
        assay.setColorValue(new BigDecimal("120"));
        assay.setSucrose(new BigDecimal("98.8"));
        AssayFailedMetricVO failed = new AssayFailedMetricVO();
        failed.setMetricCode("color_value");
        failed.setMetricName("色值");
        failed.setActualValue(new BigDecimal("120"));
        failed.setMaxValue(new BigDecimal("100"));
        failed.setCompareType("lte");
        failed.setReason("实际值高于上限");
        assay.setFailedMetrics(List.of(failed));

        QualityStandardItemVO color = new QualityStandardItemVO();
        color.setMetricCode("color_value");
        color.setMetricName("色值");
        color.setMaxValue(new BigDecimal("100"));
        color.setCompareType("lte");
        QualityStandardItemVO sucrose = new QualityStandardItemVO();
        sucrose.setMetricCode("sucrose");
        sucrose.setMetricName("蔗糖分");
        sucrose.setMinValue(new BigDecimal("98"));
        sucrose.setCompareType("gte");
        AssayStandardSnapshotVO snapshot = new AssayStandardSnapshotVO();
        snapshot.setItems(List.of(color, sucrose));
        assay.setStandardSnapshot(snapshot);
        return assay;
    }
}
