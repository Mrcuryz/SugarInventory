package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.service.*;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.vo.AssayEntryPreviewVO;
import com.Laibin.SugarInventory.mapper.AssayGroupMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssayGroupEntryPreviewTest {
    private final AssayGroupMapper groups = mock(AssayGroupMapper.class);
    private final AssayGroupEntryInputService inputs = new AssayGroupEntryInputService(new AssayEntryInputValidator());
    private final AssayEntryPreviewService single = mock(AssayEntryPreviewService.class);
    private final AssayGroupEntryPreviewService service = new AssayGroupEntryPreviewService(groups, inputs, single);

    @Test void expandsExactlyUniqueMembersInStableOrderWithIndependentVersionsAndJudgements() {
        when(groups.selectById(9)).thenReturn(group("3, 2,3"));
        when(single.preview(any())).thenAnswer(call -> {
            AssayEntryPreviewDTO item = call.getArgument(0);
            assertThat(item.getColorValue()).isEqualByComparingTo("0");
            assertThat(item.getDryWeight()).isNull();
            return AssayEntryPreviewVO.builder().productId(item.getProductId()).productName("产品"+item.getProductId())
                    .previewOnly(true).proposedVersion(item.getProductId() == 2 ? 1 : 4)
                    .judgementLabel(item.getProductId() == 2 ? "合格" : "不合格").build();
        });
        var result = service.preview(request());
        assertThat(result.productCount()).isEqualTo(2);
        assertThat(result.products()).extracting(AssayEntryPreviewVO::getProductId).containsExactly(2, 3);
        assertThat(result.products()).extracting(AssayEntryPreviewVO::getProposedVersion).containsExactly(1, 4);
        assertThat(result.products()).extracting(AssayEntryPreviewVO::getJudgementLabel).containsExactly("合格", "不合格");
        assertThat(result.previewOnly()).isTrue(); assertThat(result.confirmationAvailable()).isFalse();
        verify(groups, never()).selectForAssayEntryUpdate(anyInt());
    }

    @Test void rejectsEmptyMalformedAndNonpositiveMembershipRatherThanSilentlyDroppingEntries() {
        for (String raw : new String[]{"", " ", "2,", "2,,3", "2,x", "0", "-1", "2147483648"})
            assertThatThrownBy(() -> inputs.members(group(raw))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> inputs.members(null)).isInstanceOf(BusinessException.class);
    }

    @Test void boundedGroupIsNotTruncated() {
        ReflectionTestUtils.setField(inputs, "maxProducts", 2);
        assertThat(inputs.members(group("2,3"))).containsExactly(2, 3);
        assertThatThrownBy(() -> inputs.members(group("2,3,4"))).isInstanceOf(BusinessException.class).hasMessageContaining("最多预览2个");
    }

    @Test void refusesClientSuppliedProductAndExtraMembersOrMetrics() {
        var product = request(); product.getValues().setProductId(2);
        assertThatThrownBy(() -> service.preview(product)).isInstanceOf(BusinessException.class);
        var extra = request(); extra.reject("productIds", List.of(2, 3));
        assertThatThrownBy(() -> service.preview(extra)).isInstanceOf(BusinessException.class);
        var metric = request(); metric.getValues().recordUnsupportedField("sugarRate", 99);
        assertThatThrownBy(() -> service.preview(metric)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(groups, single);
    }

    @Test void keepsExactPrecisionRulesAndDoesNotMutateSharedInput() {
        var request = request();
        var one = inputs.inputFor(request, 2); var two = inputs.inputFor(request, 3);
        assertThat(request.getValues().getProductId()).isNull();
        assertThat(one.getProductId()).isEqualTo(2); assertThat(two.getProductId()).isEqualTo(3);
        request.getValues().setReducingSugar(new BigDecimal("0.123"));
        assertThatThrownBy(() -> inputs.inputFor(request, 2)).isInstanceOf(BusinessException.class).hasMessageContaining("两位小数");
    }

    @Test void missingMemberRejectsWholePreview() {
        when(groups.selectById(9)).thenReturn(group("2,3"));
        when(single.preview(any())).thenAnswer(call -> {
            AssayEntryPreviewDTO item = call.getArgument(0);
            if (item.getProductId() == 3) throw new BusinessException(404, "产品不存在");
            return AssayEntryPreviewVO.builder().productId(2).build();
        });
        assertThatThrownBy(() -> service.preview(request())).isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
    }

    static AssayGroupEntryPreviewDTO request() {
        var request = new AssayGroupEntryPreviewDTO(); request.setGroupId(9);
        var values = new AssayEntryPreviewDTO(); values.setSampleDate(LocalDate.of(2026, 9, 24)); values.setColorValue(BigDecimal.ZERO);
        request.setValues(values); return request;
    }
    static AssayGroup group(String ids) { var group = new AssayGroup(); group.setId(9); group.setStandardName("测试组"); group.setRelatedProducts(ids); return group; }
}
