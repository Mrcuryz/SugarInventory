package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.AssayEntryInputValidator;
import com.Laibin.SugarInventory.agent.service.AssayEntryPreviewService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayFailedMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardSnapshotVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardItemVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.AssayStandardJudgeService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryPreviewServiceTest {
    private final ProductMapper products = mock(ProductMapper.class);
    private final AssayMapper assays = mock(AssayMapper.class);
    private final AssayStandardJudgeService judge = mock(AssayStandardJudgeService.class);
    private final AssayEntryPreviewService service = new AssayEntryPreviewService(products, assays, judge, new AssayEntryInputValidator());
    private Product product;

    @BeforeEach
    void setup() {
        product = new Product();
        product.setId(84);
        product.setProductName("黄冰糖（袋）");
        product.setWeightPerPiece(new BigDecimal("25"));
        product.setPiecesPerPallet(40);
        // SQL MAX on an empty set returns NULL; Mockito's boxed Integer default is 0.
        when(assays.selectMaxVersionByProductIdAndDate(any(), any())).thenReturn(null);
    }

    @ParameterizedTest
    @CsvSource({"PASS,合格", "FAIL,不合格", "NO_STANDARD,无标准", "MULTIPLE_CANDIDATES,合格（多个匹配标准）"})
    void previewsExistingJudgementInUserLanguageWithoutAnyWrite(String result, String label) {
        when(products.selectById(84)).thenReturn(product);
        var outcome = outcome(result);
        when(judge.judge(eq(product), any())).thenReturn(outcome);
        var preview = service.preview(AssayEntryInputValidatorTest.valid());
        assertThat(preview.isPreviewOnly()).isTrue();
        assertThat(preview.getProductName()).isEqualTo("黄冰糖（袋）");
        assertThat(preview.getExistingVersion()).isNull();
        assertThat(preview.getProposedVersion()).isEqualTo(1);
        assertThat(preview.getJudgementLabel()).isEqualTo(label);
        assertThat(preview.getMetrics()).hasSize(7);
        assertThat(preview.getMetrics().get(0).getValue()).isEqualByComparingTo("268");
        assertThat(preview.getMetrics().get(0).isProvided()).isTrue();
        assertThat(preview.getMetrics().get(1).getValue()).isNull();
        assertThat(preview.getMetrics().get(1).isProvided()).isFalse();
        assertThat(preview.getMetrics()).allSatisfy(metric -> assertThat(metric.getUnit()).isNull());
        assertThat(preview.getWarnings()).anyMatch(message -> message.contains("未保存"));
        var input = ArgumentCaptor.forClass(AssaySubmitDTO.class);
        verify(judge).judge(eq(product), input.capture());
        assertThat(input.getValue().getReducingSugar()).isNull();
        verify(products).selectById(84);
        verify(assays).selectByProductIdAndDate(84, LocalDate.of(2026, 9, 23));
        verify(assays).selectMaxVersionByProductIdAndDate(84, LocalDate.of(2026, 9, 23));
        verifyNoMoreInteractions(products, assays, judge);
    }

    @Test
    void displaysNextVersionAndRegisteredUnitsButNeverMutatesOldRecord() {
        when(products.selectById(84)).thenReturn(product);
        Assay old = new Assay(); old.setId(22); old.setVersion(3);
        when(assays.selectByProductIdAndDate(any(), any())).thenReturn(old);
        when(assays.selectMaxVersionByProductIdAndDate(any(), any())).thenReturn(3);
        var outcome = outcome("FAIL");
        outcome.setAppliedStandardName("现有产品标准"); outcome.setAppliedStandardVersion(4);
        var snapshot = new AssayStandardSnapshotVO();
        var dry = new QualityStandardItemVO(); dry.setMetricCode("dry_weight_loss"); dry.setUnit("%");
        snapshot.setItems(List.of(dry)); outcome.setStandardSnapshot(snapshot);
        var failure = new AssayFailedMetricVO(); failure.setMetricName("干燥失重"); failure.setReason("缺少检测值");
        outcome.setFailedMetrics(List.of(failure));
        when(judge.judge(eq(product), any())).thenReturn(outcome);
        var preview = service.preview(AssayEntryInputValidatorTest.valid());
        assertThat(preview.getExistingVersion()).isEqualTo(3);
        assertThat(preview.getProposedVersion()).isEqualTo(4);
        assertThat(old.getVersion()).isEqualTo(3);
        assertThat(preview.getWarnings()).anyMatch(message -> message.contains("不覆盖旧记录"));
        assertThat(preview.getMetrics().get(2).getUnit()).isEqualTo("%");
        assertThat(preview.getMetrics().get(2).getValue()).isNull();
        assertThat(preview.getFailedMetrics().get(0).getReason()).isEqualTo("缺少检测值");
        assertThat(preview.getAppliedStandardVersion()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"2,3", "0,0", "2147483647,2147483647"})
    void rejectsInconsistentOrUnallocatableVersions(int latest, int max) {
        when(products.selectById(84)).thenReturn(product);
        var old = new Assay(); old.setVersion(latest);
        when(assays.selectByProductIdAndDate(any(), any())).thenReturn(old);
        when(assays.selectMaxVersionByProductIdAndDate(any(), any())).thenReturn(max);
        assertThatThrownBy(() -> service.preview(AssayEntryInputValidatorTest.valid()))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.ASSAY_ENTRY_VERSION_CONFLICT.getCode());
        verifyNoInteractions(judge);
    }

    @Test
    void rejectsInvalidRequestBeforeReadingBusinessData() {
        var request = AssayEntryInputValidatorTest.valid(); request.setPhValue(new BigDecimal("15"));
        assertThatThrownBy(() -> service.preview(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(products, assays, judge);
    }

    @Test
    void refusesMissingProductOrUnknownJudgementInsteadOfInventingSuccess() {
        assertThatThrownBy(() -> service.preview(AssayEntryInputValidatorTest.valid())).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND.getCode());
        verifyNoInteractions(assays, judge);
        when(products.selectById(84)).thenReturn(product);
        when(judge.judge(eq(product), any())).thenReturn(outcome("FUTURE_ENUM"));
        assertThatThrownBy(() -> service.preview(AssayEntryInputValidatorTest.valid())).isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE.getCode());
    }

    @Test
    void explicitlyUsesReadOnlyTransaction() {
        assertThat(AssayEntryPreviewService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void rejectsInconsistentEmptyLatestAndMissingJudgement() {
        when(products.selectById(84)).thenReturn(product);
        when(assays.selectMaxVersionByProductIdAndDate(any(), any())).thenReturn(2);
        assertThatThrownBy(() -> service.preview(AssayEntryInputValidatorTest.valid()))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.ASSAY_ENTRY_VERSION_CONFLICT.getCode());
        verifyNoInteractions(judge);
        when(assays.selectMaxVersionByProductIdAndDate(any(), any())).thenReturn(null);
        assertThatThrownBy(() -> service.preview(AssayEntryInputValidatorTest.valid()))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE.getCode());
    }

    private AssayJudgeOutcome outcome(String code) {
        var result = new AssayJudgeOutcome(); result.setJudgeResult(code);
        result.setJudgeMessage("按现有服务判定"); return result;
    }
}
