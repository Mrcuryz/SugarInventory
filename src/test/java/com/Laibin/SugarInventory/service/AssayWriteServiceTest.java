package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayWriteServiceTest {
    private final ProductMapper products = mock(ProductMapper.class);
    private final AssayMapper assays = mock(AssayMapper.class);
    private final AssayStandardJudgeService judge = mock(AssayStandardJudgeService.class);
    private final AssayWriteService service = new AssayWriteService(products, assays, judge);

    @Test
    void locksBatchInSortedDistinctOrderBeforeWriting() {
        when(products.selectForAssayUpdate(anyInt())).thenAnswer(call -> product(call.getArgument(0)));
        assertThat(service.lockProducts(List.of(9, 2, 9, 5)).keySet()).containsExactly(2, 5, 9);
        var order = inOrder(products);
        order.verify(products).selectForAssayUpdate(2);
        order.verify(products).selectForAssayUpdate(5);
        order.verify(products).selectForAssayUpdate(9);
        verifyNoMoreInteractions(products);
        verifyNoInteractions(assays, judge);
    }

    @Test
    void validatesEntireLockSetBeforeTakingAnyLock() {
        assertThatThrownBy(() -> service.lockProducts(Arrays.asList(2, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.lockProducts(List.of(2, 0))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.lockProducts(List.of())).isInstanceOf(BusinessException.class);
        verifyNoInteractions(products, assays, judge);
    }

    @Test
    void missingProductNeverJudgesOrWrites() {
        assertThatThrownBy(() -> service.append(input(), 7)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(assays, judge);
    }

    @Test
    void appendsVersionOnePreservingZeroNullAndSavedJudgement() {
        prepare();
        Assay result = service.append(input(), 7);
        assertThat(result.getVersion()).isEqualTo(1);
        assertThat(result.getColorValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getReducingSugar()).isNull();
        assertThat(result.getProductId()).isEqualTo(2);
        assertThat(result.getTestedBy()).isEqualTo(7);
        assertThat(result.getJudgeResult()).isEqualTo("FAIL");
        assertThat(result.getFailedMetricsJson()).isEqualTo("[]");
        assertThat(result.getStandardSnapshotJson()).isEqualTo("{}");
        assertThat(result.getCreatedAt()).isNotNull();
        var order = inOrder(products, assays, judge);
        order.verify(products).selectForAssayUpdate(2);
        order.verify(assays).selectHighestVersionForUpdate(2, input().getSampleDate());
        order.verify(judge).judge(any(), any());
        order.verify(assays).insert(result);
        verifyNoMoreInteractions(assays);
    }

    @Test
    void usesHighestVersionNotAnOlderSnapshotOrCreationTimestamp() {
        prepare();
        Assay highest = new Assay(); highest.setVersion(8);
        when(assays.selectHighestVersionForUpdate(anyInt(), any())).thenReturn(highest);
        assertThat(service.append(input(), 7).getVersion()).isEqualTo(9);
        assertThat(highest.getVersion()).isEqualTo(8);
        verify(assays, never()).selectByProductIdAndDate(any(), any());
        verify(assays, never()).updateById(any(Assay.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Integer.MAX_VALUE})
    void corruptOrOverflowingVersionsCannotWrite(Integer version) {
        when(products.selectForAssayUpdate(2)).thenReturn(product(2));
        Assay highest = new Assay(); highest.setVersion(version);
        when(assays.selectHighestVersionForUpdate(anyInt(), any())).thenReturn(highest);
        assertThatThrownBy(() -> service.append(input(), 7)).isInstanceOf(BusinessException.class);
        verify(assays, never()).insert(any(Assay.class));
        verifyNoInteractions(judge);
    }

    @Test
    void missingJudgementOrZeroInsertedRowsAreNotSuccess() {
        when(products.selectForAssayUpdate(2)).thenReturn(product(2));
        assertThatThrownBy(() -> service.append(input(), 7)).isInstanceOf(BusinessException.class);
        verify(assays, never()).insert(any(Assay.class));
        prepare();
        when(assays.insert(any(Assay.class))).thenReturn(0);
        assertThatThrownBy(() -> service.append(input(), 7)).isInstanceOf(BusinessException.class);
    }

    @Test
    void propagatesInsertFailureForOuterTransactionRollback() {
        prepare();
        when(assays.insert(any(Assay.class))).thenThrow(new IllegalStateException("insert failed"));
        assertThatThrownBy(() -> service.append(input(), 7)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void boundaryRequiresExistingTransactionAndCurrentDatabaseReads() throws Exception {
        assertThat(AssayWriteService.class.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(ProductMapper.class.getMethod("selectForAssayUpdate", Integer.class)
                .getAnnotation(Select.class).value()[0]).contains("FOR UPDATE");
        assertThat(AssayMapper.class.getMethod("selectHighestVersionForUpdate", Integer.class, LocalDate.class)
                .getAnnotation(Select.class).value()[0]).contains("ORDER BY version DESC", "FOR UPDATE");
    }

    private void prepare() {
        when(products.selectForAssayUpdate(2)).thenReturn(product(2));
        AssayJudgeOutcome outcome = new AssayJudgeOutcome(); outcome.setJudgeResult("FAIL");
        outcome.setFailedMetricsJson("[]"); outcome.setStandardSnapshotJson("{}");
        when(judge.judge(any(), any())).thenReturn(outcome);
        when(assays.insert(any(Assay.class))).thenReturn(1);
    }

    @Test
    void verifiedAppendUsesLockedJudgementWithoutSnapshotRequery() {
        when(products.selectForAssayUpdate(2)).thenReturn(product(2));
        when(assays.insert(any(Assay.class))).thenReturn(1);
        var outcome = new AssayJudgeOutcome(); outcome.setJudgeResult("PASS");
        assertThat(service.appendVerified(input(), 7, null, outcome).getJudgeResult()).isEqualTo("PASS");
        verifyNoInteractions(judge);
    }

    @Test
    void verifiedAppendRejectsConcurrentVersionChangeBeforeInsert() {
        when(products.selectForAssayUpdate(2)).thenReturn(product(2));
        var current = new Assay(); current.setVersion(1);
        when(assays.selectHighestVersionForUpdate(anyInt(), any())).thenReturn(current);
        assertThatThrownBy(() -> service.appendVerified(input(), 7, null, new AssayJudgeOutcome()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(1055);
        verify(assays, never()).insert(any(Assay.class));
        verifyNoInteractions(judge);
    }

    private static Product product(int id) { Product product = new Product(); product.setId(id); return product; }
    private static AssaySubmitDTO input() {
        AssaySubmitDTO input = new AssaySubmitDTO(); input.setProductId(2);
        input.setSampleDate(LocalDate.of(2026, 9, 24)); input.setColorValue(BigDecimal.ZERO); return input;
    }
}
