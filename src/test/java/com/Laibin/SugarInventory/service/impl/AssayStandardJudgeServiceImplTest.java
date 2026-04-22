package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ProductQualityStandardRelation;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.QualityStandardItem;
import com.Laibin.SugarInventory.mapper.ProductQualityStandardRelationMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardItemMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssayStandardJudgeServiceImplTest {

    @Mock
    private ProductQualityStandardRelationMapper relationMapper;

    @Mock
    private QualityStandardMapper qualityStandardMapper;

    @Mock
    private QualityStandardItemMapper qualityStandardItemMapper;

    @InjectMocks
    private AssayStandardJudgeServiceImpl judgeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(judgeService, "objectMapper", new ObjectMapper());
    }

    @Test
    void shouldReturnNoStandardWhenNoRelationConfigured() {
        Product product = product();
        AssaySubmitDTO dto = assay(BigDecimal.valueOf(20), BigDecimal.valueOf(99));
        when(relationMapper.selectActiveByProductId(eq(product.getId()), any(LocalDateTime.class))).thenReturn(List.of());

        AssayJudgeOutcome outcome = judgeService.judge(product, dto);

        assertEquals("NO_STANDARD", outcome.getJudgeResult());
        assertEquals("无标准", outcome.getCompatibleConclusion());
        assertEquals("[\"无\"]", outcome.getQualifiedStandardsJson());
        assertEquals(0, outcome.getFailedMetricCount());
    }

    @Test
    void shouldReturnPassWhenSingleStandardMatches() {
        Product product = product();
        ProductQualityStandardRelation relation = relation(1, 1);
        QualityStandard standard = standard(1, "中粮优级", 1);
        QualityStandardItem color = item("color_value", "色值", null, BigDecimal.valueOf(30), "lte");
        QualityStandardItem sucrose = item("sucrose", "蔗糖分", BigDecimal.valueOf(98), null, "gte");
        AssaySubmitDTO dto = assay(BigDecimal.valueOf(20), BigDecimal.valueOf(99));

        when(relationMapper.selectActiveByProductId(eq(product.getId()), any(LocalDateTime.class))).thenReturn(List.of(relation));
        when(qualityStandardMapper.selectById(1)).thenReturn(standard);
        when(qualityStandardItemMapper.selectByQualityStandardId(1)).thenReturn(List.of(color, sucrose));

        AssayJudgeOutcome outcome = judgeService.judge(product, dto);

        assertEquals("PASS", outcome.getJudgeResult());
        assertEquals("合格", outcome.getCompatibleConclusion());
        assertEquals("中粮优级", outcome.getAppliedStandardName());
        assertEquals(List.of("中粮优级"), outcome.getMatchedStandards());
        assertEquals(0, outcome.getFailedMetricCount());
        assertNotNull(outcome.getStandardSnapshotJson());
    }

    @Test
    void shouldReturnFailAndFailedMetricWhenNoStandardMatches() {
        Product product = product();
        ProductQualityStandardRelation relation = relation(1, 1);
        QualityStandard standard = standard(1, "国家一级", 2);
        QualityStandardItem color = item("color_value", "色值", null, BigDecimal.valueOf(10), "lte");
        AssaySubmitDTO dto = assay(BigDecimal.valueOf(20), BigDecimal.valueOf(99));

        when(relationMapper.selectActiveByProductId(eq(product.getId()), any(LocalDateTime.class))).thenReturn(List.of(relation));
        when(qualityStandardMapper.selectById(1)).thenReturn(standard);
        when(qualityStandardItemMapper.selectByQualityStandardId(1)).thenReturn(List.of(color));

        AssayJudgeOutcome outcome = judgeService.judge(product, dto);

        assertEquals("FAIL", outcome.getJudgeResult());
        assertEquals("不合格", outcome.getCompatibleConclusion());
        assertEquals("国家一级", outcome.getAppliedStandardName());
        assertEquals(1, outcome.getFailedMetricCount());
        assertEquals("color_value", outcome.getFailedMetrics().get(0).getMetricCode());
        assertTrue(outcome.getFailedMetrics().get(0).getReason().contains("上限"));
    }

    private Product product() {
        Product product = new Product();
        product.setId(100);
        product.setProductType("黄冰糖");
        return product;
    }

    private ProductQualityStandardRelation relation(Integer id, Integer qualityStandardId) {
        ProductQualityStandardRelation relation = new ProductQualityStandardRelation();
        relation.setId(id);
        relation.setProductId(100);
        relation.setQualityStandardId(qualityStandardId);
        relation.setIsDefault(true);
        relation.setPriority(1);
        relation.setEnabled(true);
        return relation;
    }

    private QualityStandard standard(Integer id, String name, Integer version) {
        QualityStandard standard = new QualityStandard();
        standard.setId(id);
        standard.setStandardName(name);
        standard.setVersion(version);
        standard.setStatus("ENABLED");
        standard.setProductType("黄冰糖");
        return standard;
    }

    private QualityStandardItem item(String metricCode,
                                     String metricName,
                                     BigDecimal minValue,
                                     BigDecimal maxValue,
                                     String compareType) {
        QualityStandardItem item = new QualityStandardItem();
        item.setMetricCode(metricCode);
        item.setMetricName(metricName);
        item.setMinValue(minValue);
        item.setMaxValue(maxValue);
        item.setCompareType(compareType);
        return item;
    }

    private AssaySubmitDTO assay(BigDecimal colorValue, BigDecimal sucrose) {
        AssaySubmitDTO dto = new AssaySubmitDTO();
        dto.setColorValue(colorValue);
        dto.setSucrose(sucrose);
        return dto;
    }
}
