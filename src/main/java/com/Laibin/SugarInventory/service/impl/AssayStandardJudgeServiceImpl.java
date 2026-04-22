package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ProductQualityStandardRelation;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.po.QualityStandardItem;
import com.Laibin.SugarInventory.domain.vo.AssayAppliedStandardVO;
import com.Laibin.SugarInventory.domain.vo.AssayFailedMetricVO;
import com.Laibin.SugarInventory.domain.vo.AssayStandardSnapshotVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardItemVO;
import com.Laibin.SugarInventory.mapper.ProductQualityStandardRelationMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardItemMapper;
import com.Laibin.SugarInventory.mapper.QualityStandardMapper;
import com.Laibin.SugarInventory.service.AssayStandardJudgeService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AssayStandardJudgeServiceImpl implements AssayStandardJudgeService {

    @Autowired
    private ProductQualityStandardRelationMapper relationMapper;

    @Autowired
    private QualityStandardMapper qualityStandardMapper;

    @Autowired
    private QualityStandardItemMapper qualityStandardItemMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AssayJudgeOutcome judge(Product product, AssaySubmitDTO dto) {
        if (product == null) {
            throw new BusinessException("产品不存在，无法执行化验判定");
        }

        List<ProductQualityStandardRelation> relations = relationMapper.selectActiveByProductId(product.getId(), LocalDateTime.now());
        if (relations.isEmpty()) {
            return buildNoStandardOutcome();
        }

        List<CandidateResult> candidates = new ArrayList<>();
        for (ProductQualityStandardRelation relation : relations) {
            QualityStandard standard = qualityStandardMapper.selectById(relation.getQualityStandardId());
            if (standard == null) {
                continue;
            }
            List<QualityStandardItem> items = qualityStandardItemMapper.selectByQualityStandardId(standard.getId());
            standard.setItems(items);
            candidates.add(evaluateCandidate(standard, dto));
        }
        if (candidates.isEmpty()) {
            return buildNoStandardOutcome();
        }

        List<CandidateResult> passedCandidates = candidates.stream().filter(CandidateResult::isPassed).toList();
        if (passedCandidates.size() == 1) {
            return buildOutcome("PASS", "合格", "命中 1 个标准", passedCandidates.get(0), passedCandidates);
        }
        if (passedCandidates.size() > 1) {
            return buildOutcome("MULTIPLE_CANDIDATES", "合格", "命中多个标准，已按默认标准/优先级采用首个标准", passedCandidates.get(0), passedCandidates);
        }
        return buildOutcome("FAIL", "不合格", "未命中任何标准，已按默认标准/优先级选择参考标准", candidates.get(0), passedCandidates);
    }

    private AssayJudgeOutcome buildNoStandardOutcome() {
        AssayJudgeOutcome outcome = new AssayJudgeOutcome();
        outcome.setJudgeResult("NO_STANDARD");
        outcome.setCompatibleConclusion("无标准");
        outcome.setJudgeMessage("产品未配置启用中的化验标准");
        outcome.setFailedMetricCount(0);
        outcome.setQualifiedStandardsJson(writeJson(List.of("无")));
        outcome.setFailedMetricsJson(writeJson(List.of()));
        return outcome;
    }

    private CandidateResult evaluateCandidate(QualityStandard standard, AssaySubmitDTO dto) {
        CandidateResult result = new CandidateResult();
        result.standard = standard;
        result.failedMetrics = new ArrayList<>();
        for (QualityStandardItem item : standard.getItems()) {
            AssayFailedMetricVO failedMetric = evaluateItem(item, dto);
            if (failedMetric != null) {
                result.failedMetrics.add(failedMetric);
            }
        }
        result.passed = result.failedMetrics.isEmpty();
        return result;
    }

    private AssayFailedMetricVO evaluateItem(QualityStandardItem item, AssaySubmitDTO dto) {
        BigDecimal actualValue = resolveActualValue(item.getMetricCode(), dto);
        if (actualValue == null && item.getMinValue() == null && item.getMaxValue() == null) {
            return null;
        }
        String compareType = item.getCompareType() == null || item.getCompareType().isBlank() ? inferCompareType(item) : item.getCompareType();
        boolean passed;
        String reason = null;
        if (actualValue == null) {
            passed = false;
            reason = "缺少检测值";
        } else if ("lte".equalsIgnoreCase(compareType)) {
            passed = item.getMaxValue() == null || actualValue.compareTo(item.getMaxValue()) <= 0;
            if (!passed) {
                reason = "超出上限 " + item.getMaxValue();
            }
        } else if ("gte".equalsIgnoreCase(compareType)) {
            passed = item.getMinValue() == null || actualValue.compareTo(item.getMinValue()) >= 0;
            if (!passed) {
                reason = "低于下限 " + item.getMinValue();
            }
        } else {
            passed = (item.getMinValue() == null || actualValue.compareTo(item.getMinValue()) >= 0)
                    && (item.getMaxValue() == null || actualValue.compareTo(item.getMaxValue()) <= 0);
            if (!passed) {
                if (item.getMinValue() != null && actualValue.compareTo(item.getMinValue()) < 0) {
                    reason = "低于下限 " + item.getMinValue();
                } else if (item.getMaxValue() != null && actualValue.compareTo(item.getMaxValue()) > 0) {
                    reason = "超出上限 " + item.getMaxValue();
                }
            }
        }
        if (passed) {
            return null;
        }
        AssayFailedMetricVO failedMetric = new AssayFailedMetricVO();
        failedMetric.setMetricCode(item.getMetricCode());
        failedMetric.setMetricName(item.getMetricName());
        failedMetric.setActualValue(actualValue);
        failedMetric.setUnit(item.getUnit());
        failedMetric.setCompareType(compareType);
        failedMetric.setMinValue(item.getMinValue());
        failedMetric.setMaxValue(item.getMaxValue());
        failedMetric.setReason(reason);
        return failedMetric;
    }

    private BigDecimal resolveActualValue(String metricCode, AssaySubmitDTO dto) {
        return switch (metricCode) {
            case "color_value" -> dto.getColorValue();
            case "reducing_sugar" -> dto.getReducingSugar();
            case "dry_weight_loss", "dry_weight" -> dto.getDryWeight();
            case "conductivity_ash" -> dto.getConductivityAsh();
            case "sucrose" -> dto.getSucrose();
            case "insoluble_impurity" -> dto.getInsolubleImpurity();
            case "ph", "ph_value" -> dto.getPhValue();
            default -> null;
        };
    }

    private String inferCompareType(QualityStandardItem item) {
        if (item.getMinValue() != null && item.getMaxValue() == null) {
            return "gte";
        }
        if (item.getMinValue() == null && item.getMaxValue() != null) {
            return "lte";
        }
        return "range";
    }

    private AssayJudgeOutcome buildOutcome(String judgeResult,
                                           String compatibleConclusion,
                                           String judgeMessage,
                                           CandidateResult applied,
                                           List<CandidateResult> passedCandidates) {
        AssayJudgeOutcome outcome = new AssayJudgeOutcome();
        outcome.setJudgeResult(judgeResult);
        outcome.setCompatibleConclusion(compatibleConclusion);
        outcome.setJudgeMessage(judgeMessage);
        outcome.setAppliedStandardId(applied.standard.getId());
        outcome.setAppliedStandardName(applied.standard.getStandardName());
        outcome.setAppliedStandardVersion(applied.standard.getVersion());

        List<String> matchedNames = passedCandidates.stream().map(item -> item.standard.getStandardName()).toList();
        outcome.setMatchedStandards(matchedNames);
        outcome.setQualifiedStandardsJson(writeJson(matchedNames.isEmpty() ? List.of("无") : matchedNames));
        outcome.setFailedMetrics(applied.failedMetrics);
        outcome.setFailedMetricCount(applied.failedMetrics.size());
        outcome.setFailedMetricsJson(writeJson(applied.failedMetrics));

        AssayAppliedStandardVO appliedStandard = new AssayAppliedStandardVO();
        BeanUtils.copyProperties(applied.standard, appliedStandard);
        outcome.setAppliedStandard(appliedStandard);

        AssayStandardSnapshotVO snapshot = new AssayStandardSnapshotVO();
        BeanUtils.copyProperties(applied.standard, snapshot);
        snapshot.setItems(applied.standard.getItems().stream().map(this::convertItemToVO).toList());
        outcome.setStandardSnapshot(snapshot);
        outcome.setStandardSnapshotJson(writeJson(snapshot));
        return outcome;
    }

    private QualityStandardItemVO convertItemToVO(QualityStandardItem item) {
        QualityStandardItemVO vo = new QualityStandardItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("化验判定结果序列化失败");
        }
    }

    private static class CandidateResult {
        private QualityStandard standard;
        private boolean passed;
        private List<AssayFailedMetricVO> failedMetrics;

        public boolean isPassed() {
            return passed;
        }
    }
}
