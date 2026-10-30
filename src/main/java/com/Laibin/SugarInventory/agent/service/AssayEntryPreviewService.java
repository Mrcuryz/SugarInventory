package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.vo.AssayEntryPreviewVO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardItemVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.AssayStandardJudgeService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssayEntryPreviewService {
    private final ProductMapper productMapper;
    private final AssayMapper assayMapper;
    private final AssayStandardJudgeService judgeService;
    private final AssayEntryInputValidator inputValidator;

    public AssayEntryPreviewVO preview(AssayEntryPreviewDTO request) {
        AssaySubmitDTO input = inputValidator.normalize(request);
        Product product = productMapper.selectById(input.getProductId());
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        Assay latest = assayMapper.selectByProductIdAndDate(input.getProductId(), input.getSampleDate());
        Integer maxVersion = assayMapper.selectMaxVersionByProductIdAndDate(input.getProductId(), input.getSampleDate());
        if ((latest == null) != (maxVersion == null)
                || (latest != null && (!Objects.equals(latest.getVersion(), maxVersion)
                || maxVersion < 1 || maxVersion == Integer.MAX_VALUE))) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_VERSION_CONFLICT);
        }
        AssayJudgeOutcome outcome = judgeService.judge(product, input);
        return project(input, product, maxVersion, outcome);
    }

    /** Shared presentation for a read-only query or a server-owned locked preview. */
    public AssayEntryPreviewVO project(AssaySubmitDTO input, Product product, Integer maxVersion, AssayJudgeOutcome outcome) {
        String label = judgementLabel(outcome);
        List<AssayEntryPreviewVO.Metric> metrics = List.of(
                metric("colorValue", "色值", "color_value", input.getColorValue(), outcome),
                metric("reducingSugar", "还原糖分", "reducing_sugar", input.getReducingSugar(), outcome),
                metric("dryWeight", "干燥失重", "dry_weight", input.getDryWeight(), outcome),
                metric("conductivityAsh", "电导灰分", "conductivity_ash", input.getConductivityAsh(), outcome),
                metric("sucrose", "蔗糖分", "sucrose", input.getSucrose(), outcome),
                metric("insolubleImpurity", "不溶于水杂质", "insoluble_impurity", input.getInsolubleImpurity(), outcome),
                metric("phValue", "pH值", "ph_value", input.getPhValue(), outcome));
        List<String> warnings = new ArrayList<>();
        warnings.add("本次仅预览，未保存化验记录，也未生成可执行的确认凭据。");
        if (maxVersion != null) warnings.add("该产品在此采样日期已有化验记录；再次录入会新增版本，不覆盖旧记录。");
        if (metrics.stream().anyMatch(metric -> !metric.isProvided())) warnings.add("未填写的检测项目保留为空，不以0补齐。");
        if ("NO_STANDARD".equals(outcome.getJudgeResult())) warnings.add("产品未配置启用中的化验标准，不能据此认定合格。");
        if ("MULTIPLE_CANDIDATES".equals(outcome.getJudgeResult())) warnings.add("命中多个标准，按现有默认标准/优先级预判；请核对采用标准。");
        return AssayEntryPreviewVO.builder()
                .previewOnly(true).previewedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
                .productId(product.getId()).productName(product.getProductName())
                .weightPerPiece(product.getWeightPerPiece()).piecesPerPallet(product.getPiecesPerPallet())
                .sampleDate(input.getSampleDate()).existingVersion(maxVersion)
                .proposedVersion(maxVersion == null ? 1 : maxVersion + 1)
                .metrics(metrics).judgementLabel(label).judgementMessage(outcome.getJudgeMessage())
                .appliedStandardName(outcome.getAppliedStandardName())
                .appliedStandardVersion(outcome.getAppliedStandardVersion())
                .failedMetrics(outcome.getFailedMetrics() == null ? List.of() : outcome.getFailedMetrics().stream()
                        .map(item -> AssayEntryPreviewVO.FailedMetric.builder().name(item.getMetricName())
                                .value(item.getActualValue()).unit(item.getUnit()).reason(item.getReason()).build()).toList())
                .warnings(List.copyOf(warnings)).build();
    }

    private String judgementLabel(AssayJudgeOutcome outcome) {
        if (outcome == null || outcome.getJudgeResult() == null) throw new BusinessException(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE);
        return switch (outcome.getJudgeResult()) {
            case "PASS" -> "合格";
            case "FAIL" -> "不合格";
            case "NO_STANDARD" -> "无标准";
            case "MULTIPLE_CANDIDATES" -> "合格（多个匹配标准）";
            default -> throw new BusinessException(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE);
        };
    }

    private AssayEntryPreviewVO.Metric metric(String field, String name, String code,
                                              BigDecimal value, AssayJudgeOutcome outcome) {
        List<QualityStandardItemVO> items = outcome.getStandardSnapshot() == null
                || outcome.getStandardSnapshot().getItems() == null ? List.of() : outcome.getStandardSnapshot().getItems();
        // Units are displayed only when unambiguous in the selected standard; no inferred conversion.
        List<String> units = items.stream().filter(item -> code.equals(canonicalMetric(item.getMetricCode())))
                .map(QualityStandardItemVO::getUnit).filter(unit -> unit != null && !unit.isBlank()).distinct().toList();
        return AssayEntryPreviewVO.Metric.builder().field(field).name(name).value(value)
                .provided(value != null).unit(units.size() == 1 ? units.get(0) : null).build();
    }

    private String canonicalMetric(String code) {
        if ("dry_weight_loss".equals(code)) return "dry_weight";
        if ("ph".equals(code)) return "ph_value";
        return code;
    }
}
