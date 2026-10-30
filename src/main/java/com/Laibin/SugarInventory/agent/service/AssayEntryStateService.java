package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.AssayStandardJudgeService;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

/** Locks the complete judgement dependency set, including currently disabled/future relations. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AssayEntryStateService {
    private final AssayWriteService writer;
    private final AssayMapper assays;
    private final ProductQualityStandardRelationMapper relations;
    private final QualityStandardMapper standards;
    private final QualityStandardItemMapper items;
    private final AssayStandardJudgeService judge;
    private final ObjectMapper objectMapper;

    public State lockAndRead(AssaySubmitDTO input) {
        return lockAndReadAll(List.of(input)).get(0);
    }

    /** Lock the complete batch dependency set globally, avoiding per-product standard lock inversions. */
    public List<State> lockAndReadAll(List<AssaySubmitDTO> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.stream().anyMatch(input -> input == null || input.getProductId() == null
                || input.getProductId() <= 0 || input.getSampleDate() == null)
                || inputs.stream().map(AssaySubmitDTO::getProductId).distinct().count() != inputs.size()) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_INPUT_INVALID);
        }
        var ordered = inputs.stream().sorted(Comparator.comparing(AssaySubmitDTO::getProductId)).toList();
        var products = writer.lockProducts(ordered.stream().map(AssaySubmitDTO::getProductId).toList());
        Map<Integer, Assay> latestByProduct = new HashMap<>();
        Map<Integer, List<ProductQualityStandardRelation>> relationsByProduct = new LinkedHashMap<>();
        for (var input : ordered) {
            latestByProduct.put(input.getProductId(), assays.selectHighestVersionForUpdate(input.getProductId(), input.getSampleDate()));
            relationsByProduct.put(input.getProductId(), relations.selectAllForAssayUpdate(input.getProductId()));
        }
        Map<Integer, QualityStandard> standardsById = new TreeMap<>();
        var ids = relationsByProduct.values().stream().flatMap(Collection::stream)
                .map(ProductQualityStandardRelation::getQualityStandardId).distinct().sorted().toList();
        for (Integer id : ids) {
            QualityStandard standard = standards.selectForAssayUpdate(id);
            if (standard == null) throw new BusinessException(ErrorCode.ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE);
            standard.setItems(items.selectForAssayUpdate(id));
            standardsById.put(id, standard);
        }
        LocalDateTime at = LocalDateTime.now();
        return ordered.stream().map(input -> {
            var productRelations = relationsByProduct.get(input.getProductId());
            Map<Integer, QualityStandard> productStandards = new TreeMap<>();
            productRelations.forEach(relation -> productStandards.put(relation.getQualityStandardId(), standardsById.get(relation.getQualityStandardId())));
            return projectState(input, products.get(input.getProductId()), latestByProduct.get(input.getProductId()), productRelations, productStandards, at);
        }).toList();
    }

    private State projectState(AssaySubmitDTO input, Product product, Assay latest,
                               List<ProductQualityStandardRelation> allRelations, Map<Integer, QualityStandard> allStandards, LocalDateTime at) {
        Integer version = latest == null ? null : latest.getVersion();
        if (latest != null && (version == null || version < 1 || version == Integer.MAX_VALUE)) {
            throw new BusinessException(ErrorCode.ASSAY_ENTRY_VERSION_CONFLICT);
        }
        List<ProductQualityStandardRelation> active = allRelations.stream()
                .filter(relation -> Boolean.TRUE.equals(relation.getEnabled())
                        && (relation.getEffectiveFrom() == null || !relation.getEffectiveFrom().isAfter(at))
                        && (relation.getEffectiveTo() == null || !relation.getEffectiveTo().isBefore(at)))
                .filter(relation -> {
                    String status = allStandards.get(relation.getQualityStandardId()).getStatus();
                    return status == null || status.isEmpty() || "ENABLED".equals(status);
                })
                .sorted(Comparator.comparing(ProductQualityStandardRelation::getIsDefault, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ProductQualityStandardRelation::getPriority, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ProductQualityStandardRelation::getId)).toList();
        AssayJudgeOutcome outcome = judge.judgeStandards(active.stream()
                .map(relation -> allStandards.get(relation.getQualityStandardId())).toList(), input);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("product", product); snapshot.put("latestAssay", latest);
        snapshot.put("relations", allRelations); snapshot.put("standards", allStandards);
        snapshot.put("activeRelationIds", active.stream().map(ProductQualityStandardRelation::getId).toList());
        snapshot.put("judgement", outcome);
        return new State(product, version, outcome, json(snapshot));
    }

    public String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法保存化验预览快照", exception); }
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public record State(Product product, Integer existingVersion, AssayJudgeOutcome judgement, String snapshotJson) {
        public String digest() { return AssayEntryStateService.digest(snapshotJson); }
    }
}
