package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.dto.QualityStandardItemDTO;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.QualityStandardItem;
import com.Laibin.SugarInventory.domain.vo.QualityStandardRelatedProductVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductQualityStandardRelationMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardItemMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityStandardServiceImplTest {

    @Mock
    private QualityStandardMapper qualityStandardMapper;

    @Mock
    private QualityStandardItemMapper qualityStandardItemMapper;

    @Mock
    private ProductQualityStandardRelationMapper relationMapper;

    @Mock
    private AssayMapper assayMapper;

    @InjectMocks
    private QualityStandardServiceImpl qualityStandardService;

    @Test
    void shouldPassStatusFilterToMapperWhenListingStandards() {
        when(qualityStandardMapper.selectByConditions("黄冰糖", "中粮", "ENABLED")).thenReturn(List.of());

        qualityStandardService.listQualityStandards("黄冰糖", "中粮", "ENABLED");

        verify(qualityStandardMapper).selectByConditions("黄冰糖", "中粮", "ENABLED");
    }

    @Test
    void shouldAttachRelatedProductsWhenListingStandards() {
        QualityStandard standard = new QualityStandard();
        standard.setId(7);
        standard.setStandardName("中粮优级");
        when(qualityStandardMapper.selectByConditions(null, null, "ENABLED")).thenReturn(List.of(standard));
        when(qualityStandardItemMapper.selectByQualityStandardId(7)).thenReturn(List.of());
        when(relationMapper.selectRelatedProductsByStandardIds(List.of(7))).thenReturn(List.of(relatedProduct(7, 101, "一级黄冰糖")));

        var result = qualityStandardService.listQualityStandards(null, null, "ENABLED");

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getRelatedProducts().size());
        assertEquals("一级黄冰糖", result.get(0).getRelatedProducts().get(0).getProductName());
    }

    @Test
    void shouldNormalizeSevenFixedMetricsWhenAddingStandard() {
        QualityStandardDTO dto = standardDto();
        List<QualityStandardItem> savedItems = new ArrayList<>();

        when(qualityStandardMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            QualityStandard standard = invocation.getArgument(0);
            standard.setId(99);
            return 1;
        }).when(qualityStandardMapper).insert(any(QualityStandard.class));
        doAnswer(invocation -> {
            savedItems.add(invocation.getArgument(0));
            return 1;
        }).when(qualityStandardItemMapper).insert(any(QualityStandardItem.class));
        when(qualityStandardItemMapper.selectByQualityStandardId(99)).thenReturn(List.of());

        qualityStandardService.addQualityStandard(dto);

        ArgumentCaptor<QualityStandard> standardCaptor = ArgumentCaptor.forClass(QualityStandard.class);
        verify(qualityStandardMapper).insert(standardCaptor.capture());
        assertEquals("ENABLED", standardCaptor.getValue().getStatus());
        assertEquals(1, standardCaptor.getValue().getVersion());

        assertEquals(7, savedItems.size());
        assertEquals("色值", savedItems.get(0).getMetricName());
        assertEquals("IU", savedItems.get(0).getUnit());
        assertEquals("lte", savedItems.get(0).getCompareType());
        assertEquals("蔗糖分", savedItems.get(4).getMetricName());
        assertEquals("gte", savedItems.get(4).getCompareType());
        assertEquals("pH", savedItems.get(6).getMetricName());
        assertEquals("", savedItems.get(6).getUnit());
    }

    @Test
    void shouldRejectWhenFixedMetricsAreIncomplete() {
        QualityStandardDTO dto = standardDto();
        dto.getItems().remove(dto.getItems().size() - 1);

        when(qualityStandardMapper.selectOne(any())).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> qualityStandardService.addQualityStandard(dto));

        assertEquals("请完整配置 7 项固定指标", error.getMessage());
        verify(qualityStandardMapper, never()).insert(any(QualityStandard.class));
        verify(qualityStandardItemMapper, never()).insert(any(QualityStandardItem.class));
    }

    @Test
    void shouldRejectDeleteWhenStandardIsStillBound() {
        QualityStandard standard = new QualityStandard();
        standard.setId(12);
        when(qualityStandardMapper.selectById(12)).thenReturn(standard);
        when(relationMapper.countByQualityStandardId(12)).thenReturn(2);

        BusinessException error = assertThrows(BusinessException.class, () -> qualityStandardService.deleteQualityStandard(12));

        assertEquals("当前标准正在被使用！", error.getMessage());
        verify(qualityStandardItemMapper, never()).deleteByQualityStandardId(any());
        verify(qualityStandardMapper, never()).deleteById(12);
    }

    @Test
    void shouldForceDeleteStandardAndRelations() {
        QualityStandard standard = new QualityStandard();
        standard.setId(12);
        when(qualityStandardMapper.selectById(12)).thenReturn(standard);
        when(assayMapper.countByAppliedStandardId(12)).thenReturn(0);
        when(qualityStandardMapper.deleteById(12)).thenReturn(1);

        qualityStandardService.forceDeleteQualityStandard(12);

        verify(relationMapper).deleteByQualityStandardId(12);
        verify(qualityStandardItemMapper).deleteByQualityStandardId(12);
        verify(qualityStandardMapper).deleteById(12);
    }

    @Test
    void shouldRejectForceDeleteWhenAssayHistoryExists() {
        QualityStandard standard = new QualityStandard();
        standard.setId(12);
        when(qualityStandardMapper.selectById(12)).thenReturn(standard);
        when(assayMapper.countByAppliedStandardId(12)).thenReturn(3);

        BusinessException error = assertThrows(BusinessException.class, () -> qualityStandardService.forceDeleteQualityStandard(12));

        assertEquals("当前标准已被历史化验记录引用，不能强制删除", error.getMessage());
        verify(relationMapper, never()).deleteByQualityStandardId(12);
    }

    private QualityStandardDTO standardDto() {
        QualityStandardDTO dto = new QualityStandardDTO();
        dto.setProductType("黄冰糖");
        dto.setStandardName("中粮优级");
        dto.setStatus("ENABLED");
        dto.setItems(new ArrayList<>(List.of(
                item("color_value", null, BigDecimal.valueOf(30)),
                item("reducing_sugar", BigDecimal.valueOf(6), BigDecimal.valueOf(8)),
                item("dry_weight_loss", null, BigDecimal.valueOf(0.12)),
                item("conductivity_ash", null, BigDecimal.valueOf(0.08)),
                item("sucrose", BigDecimal.valueOf(98), null),
                item("insoluble_impurity", null, BigDecimal.valueOf(20)),
                item("ph", BigDecimal.valueOf(6.5), BigDecimal.valueOf(7.5))
        )));
        return dto;
    }

    private QualityStandardItemDTO item(String metricCode, BigDecimal minValue, BigDecimal maxValue) {
        QualityStandardItemDTO dto = new QualityStandardItemDTO();
        dto.setMetricCode(metricCode);
        dto.setMetricName("自定义名称会被覆盖");
        dto.setUnit("自定义单位会被覆盖");
        dto.setMinValue(minValue);
        dto.setMaxValue(maxValue);
        return dto;
    }

    private QualityStandardRelatedProductVO relatedProduct(Integer standardId, Integer productId, String productName) {
        QualityStandardRelatedProductVO vo = new QualityStandardRelatedProductVO();
        vo.setQualityStandardId(standardId);
        vo.setProductId(productId);
        vo.setProductName(productName);
        return vo;
    }
}
