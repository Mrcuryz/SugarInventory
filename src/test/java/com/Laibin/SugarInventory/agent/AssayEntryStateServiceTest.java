package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.AssayEntryStateService;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.AssayStandardJudgeService;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayEntryStateServiceTest {
    private final AssayWriteService writer = mock(AssayWriteService.class);
    private final AssayMapper assays = mock(AssayMapper.class);
    private final ProductQualityStandardRelationMapper relations = mock(ProductQualityStandardRelationMapper.class);
    private final QualityStandardMapper standards = mock(QualityStandardMapper.class);
    private final QualityStandardItemMapper items = mock(QualityStandardItemMapper.class);
    private final AssayStandardJudgeService judge = mock(AssayStandardJudgeService.class);
    private final AssayEntryStateService service = new AssayEntryStateService(writer, assays, relations, standards, items, judge, new ObjectMapper().findAndRegisterModules());

    @Test
    void locksAllDependenciesInIdOrderButJudgesOnlyActiveInBusinessPriorityOrder() {
        var input = input(); var product = new Product(); product.setId(84);
        when(writer.lockProducts(List.of(84))).thenReturn(Map.of(84, product));
        var primary = relation(1, 9, true, 9); var secondary = relation(2, 2, false, 1);
        var future = relation(3, 5, false, 0); future.setEffectiveFrom(LocalDateTime.now().plusDays(1));
        var disabled = relation(4, 7, false, 0); disabled.setEnabled(false);
        when(relations.selectAllForAssayUpdate(84)).thenReturn(List.of(primary, secondary, future, disabled));
        when(standards.selectForAssayUpdate(anyInt())).thenAnswer(call -> standard(call.getArgument(0)));
        when(items.selectForAssayUpdate(anyInt())).thenReturn(List.of());
        when(judge.judgeStandards(any(), any())).thenReturn(new AssayJudgeOutcome());
        var state = service.lockAndRead(input);
        var order = inOrder(standards, items);
        for (int id : List.of(2, 5, 7, 9)) {
            order.verify(standards).selectForAssayUpdate(id); order.verify(items).selectForAssayUpdate(id);
        }
        verify(judge).judgeStandards(argThat(list -> list.stream().map(QualityStandard::getId).toList().equals(List.of(9, 2))), eq(input));
        assertThat(state.snapshotJson()).contains("effectiveFrom", "activeRelationIds");
        assertThat(service.lockAndRead(input).digest()).isEqualTo(state.digest());
        verify(judge, never()).judge(any(), any());
    }

    @Test
    void unselectedStandardChangeInvalidatesSnapshot() {
        var product = new Product(); product.setId(84);
        when(writer.lockProducts(any())).thenReturn(Map.of(84, product));
        var relation = relation(1, 2, false, 1); relation.setEnabled(false);
        when(relations.selectAllForAssayUpdate(84)).thenReturn(List.of(relation));
        QualityStandard standard = standard(2); when(standards.selectForAssayUpdate(2)).thenReturn(standard);
        when(items.selectForAssayUpdate(2)).thenReturn(List.of());
        when(judge.judgeStandards(any(), any())).thenReturn(new AssayJudgeOutcome());
        var before = service.lockAndRead(input()).digest();
        standard.setVersion(2);
        assertThat(service.lockAndRead(input()).digest()).isNotEqualTo(before);
    }

    @Test
    void expiredRelationsAreExcludedFromActiveJudgement() {
        var product = new Product(); product.setId(84);
        when(writer.lockProducts(any())).thenReturn(Map.of(84, product));
        var relation = relation(1, 2, true, 0);
        when(relations.selectAllForAssayUpdate(84)).thenReturn(List.of(relation));
        when(standards.selectForAssayUpdate(2)).thenReturn(standard(2));
        when(items.selectForAssayUpdate(2)).thenReturn(List.of());
        when(judge.judgeStandards(any(), any())).thenReturn(new AssayJudgeOutcome());
        service.lockAndRead(input()); verify(judge).judgeStandards(argThat(list -> list.size() == 1), any());
        relation.setEffectiveTo(LocalDateTime.now().minusSeconds(1));
        service.lockAndRead(input()); verify(judge).judgeStandards(eq(List.of()), any());
    }

    private static ProductQualityStandardRelation relation(int id, int standard, boolean primary, int priority) {
        var relation = new ProductQualityStandardRelation(); relation.setId(id); relation.setProductId(84);
        relation.setQualityStandardId(standard); relation.setEnabled(true); relation.setIsDefault(primary); relation.setPriority(priority); return relation;
    }
    private static QualityStandard standard(int id) {
        var standard = new QualityStandard(); standard.setId(id); standard.setVersion(1); standard.setStatus("ENABLED"); return standard;
    }
    private static AssaySubmitDTO input() {
        var input = new AssaySubmitDTO(); input.setProductId(84); input.setSampleDate(LocalDate.of(2026, 9, 24)); return input;
    }

    @Test void batchLocksSharedStandardsOnceInGlobalOrderBeforeJudgingAnyProduct() {
        var one = input(); var two = input(); two.setProductId(85);
        var p1 = new Product(); p1.setId(84); var p2 = new Product(); p2.setId(85);
        when(writer.lockProducts(List.of(84, 85))).thenReturn(Map.of(84, p1, 85, p2));
        when(relations.selectAllForAssayUpdate(84)).thenReturn(List.of(relation(1, 9, true, 0)));
        when(relations.selectAllForAssayUpdate(85)).thenReturn(List.of(relation(2, 2, true, 0), relation(3, 9, false, 1)));
        when(standards.selectForAssayUpdate(anyInt())).thenAnswer(call -> standard(call.getArgument(0)));
        when(items.selectForAssayUpdate(anyInt())).thenReturn(List.of());
        when(judge.judgeStandards(any(), any())).thenReturn(new AssayJudgeOutcome());
        var result = service.lockAndReadAll(List.of(two, one));
        assertThat(result).extracting(state -> state.product().getId()).containsExactly(84, 85);
        var order = inOrder(writer, standards, judge);
        order.verify(writer).lockProducts(List.of(84, 85));
        order.verify(standards).selectForAssayUpdate(2); order.verify(standards).selectForAssayUpdate(9);
        order.verify(judge).judgeStandards(any(), eq(one)); order.verify(judge).judgeStandards(any(), eq(two));
        verify(standards, times(1)).selectForAssayUpdate(9);
        assertThatThrownBy(() -> service.lockAndReadAll(List.of(one, one))).isInstanceOf(com.Laibin.SugarInventory.common.BusinessException.class);
    }
}
