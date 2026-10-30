package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared append boundary; callers retain ownership of transaction, permissions and audit. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AssayWriteService {
    private final ProductMapper productMapper;
    private final AssayMapper assayMapper;
    private final AssayStandardJudgeService judgeService;

    /** Lock every product before a batch writes anything, in one stable order. */
    public Map<Integer, Product> lockProducts(Collection<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()
                || productIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        }
        Map<Integer, Product> products = new LinkedHashMap<>();
        for (Integer id : productIds.stream().distinct().sorted().toList()) {
            Product product = productMapper.selectForAssayUpdate(id);
            if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            products.put(id, product);
        }
        return products;
    }

    public Assay append(AssaySubmitDTO input, Integer operatorId) {
        return appendInternal(input, operatorId, false, null, null);
    }

    /** Only for a server-verified exact preview; no HTTP controller accepts a judgement from a client. */
    public Assay appendVerified(AssaySubmitDTO input, Integer operatorId, Integer expectedVersion, AssayJudgeOutcome verifiedJudgement) {
        if (verifiedJudgement == null) throw new BusinessException(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE);
        return appendInternal(input, operatorId, true, expectedVersion, verifiedJudgement);
    }

    private Assay appendInternal(AssaySubmitDTO input, Integer operatorId, boolean exact,
                                 Integer expectedVersion, AssayJudgeOutcome verifiedJudgement) {
        if (input == null || input.getProductId() == null || input.getSampleDate() == null
                || operatorId == null || operatorId <= 0) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        }
        Product product = lockProducts(java.util.List.of(input.getProductId())).get(input.getProductId());
        // A locking read observes committed writes even if the outer transaction already has a snapshot.
        Assay highest = assayMapper.selectHighestVersionForUpdate(input.getProductId(), input.getSampleDate());
        if (highest != null && (highest.getVersion() == null || highest.getVersion() < 1
                || highest.getVersion() == Integer.MAX_VALUE)) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_VERSION_CONFLICT);
        }
        if (exact && !java.util.Objects.equals(expectedVersion, highest == null ? null : highest.getVersion())) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_STATE_CHANGED);
        }
        AssayJudgeOutcome outcome = exact ? verifiedJudgement : judgeService.judge(product, input);
        if (outcome == null || outcome.getJudgeResult() == null) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE);
        }
        Assay assay = new Assay();
        BeanUtils.copyProperties(input, assay);
        assay.setTestedBy(operatorId);
        assay.setCreatedAt(LocalDateTime.now());
        assay.setVersion(highest == null ? 1 : highest.getVersion() + 1);
        assay.setQualifiedStandards(outcome.getQualifiedStandardsJson());
        assay.setIsQualified(outcome.getCompatibleConclusion());
        assay.setAppliedStandardId(outcome.getAppliedStandardId());
        assay.setAppliedStandardName(outcome.getAppliedStandardName());
        assay.setAppliedStandardVersion(outcome.getAppliedStandardVersion());
        assay.setJudgeResult(outcome.getJudgeResult());
        assay.setJudgeMessage(outcome.getJudgeMessage());
        assay.setFailedMetricCount(outcome.getFailedMetricCount());
        assay.setFailedMetricsJson(outcome.getFailedMetricsJson());
        assay.setStandardSnapshotJson(outcome.getStandardSnapshotJson());
        if (assayMapper.insert(assay) != 1) throw new BusinessException(ErrorCode.DATA_IMPORT_FAILED);
        return assay;
    }
}
