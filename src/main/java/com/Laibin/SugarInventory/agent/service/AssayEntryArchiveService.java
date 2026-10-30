package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.AgentAssayEntryPreview;
import com.Laibin.SugarInventory.domain.vo.AssayEntryArchivedPreviewVO;
import com.Laibin.SugarInventory.mapper.AgentAssayEntryPreviewMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Objects;
import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class AssayEntryArchiveService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AssayEntryAccessService access;
    private final AssayEntryInputValidator validator;
    private final AssayEntryStateService stateService;
    private final AssayEntryPreviewService presentation;
    private final AgentAssayEntryPreviewMapper previews;
    private final ObjectMapper objectMapper;
    private final AssayGroupEntryStateService groupStates;

    public AssayEntryArchivedPreviewVO create(Integer owner, String session, AssayEntryPreviewDTO request) {
        access.requireCurrent(owner, session);
        AssaySubmitDTO input = validator.normalize(request);
        var state = stateService.lockAndRead(input);
        ObjectNode payload = objectMapper.valueToTree(presentation.project(input, state.product(), state.existingVersion(), state.judgement()));
        // The archive is not confirmation or a write. The modal must show this fresh server payload before confirmation.
        payload.withArray("warnings").set(0, objectMapper.getNodeFactory().textNode("尚未保存，请核对产品、采样日期和各项检测值。"));
        return store(owner, session, stateService.json(input), state.snapshotJson(), payload);
    }

    public AssayEntryArchivedPreviewVO createGroup(Integer owner, String session, AssayGroupEntryPreviewDTO request) {
        access.requireCurrent(owner, session);
        var group = groupStates.lockAndRead(request);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("entryKind", "GROUP_V1"); payload.put("previewOnly", true);
        payload.put("groupId", group.groupId()); payload.put("groupName", group.groupName());
        payload.put("sampleDate", group.inputs().get(0).getSampleDate().toString());
        payload.put("productCount", group.products().size());
        var products = payload.putArray("products");
        for (int index = 0; index < group.inputs().size(); index++) {
            var state = group.products().get(index);
            ObjectNode item = objectMapper.valueToTree(presentation.project(group.inputs().get(index), state.product(), state.existingVersion(), state.judgement()));
            item.withArray("warnings").set(0, objectMapper.getNodeFactory().textNode("尚未保存，请核对本产品的检测值及判定。"));
            products.add(item);
        }
        payload.putArray("warnings").add("尚未保存，请核对化验组内全部产品、采样日期和检测值。")
                .add("同一组检测值将用于全部列出产品，任一产品保存失败则整组不保存。");
        // Persist an explicit payload type. Legacy single-product JSON remains unchanged.
        ObjectNode input = objectMapper.createObjectNode(); input.put("entryKind", "GROUP_V1");
        input.set("request", objectMapper.valueToTree(request));
        return store(owner, session, stateService.json(input), group.snapshotJson(), payload);
    }

    private AssayEntryArchivedPreviewVO store(Integer owner, String session, String input, String snapshot, ObjectNode payload) {
        AgentAssayEntryPreview row = new AgentAssayEntryPreview();
        byte[] nonce = new byte[32]; RANDOM.nextBytes(nonce);
        row.setPreviewRef("aep1_" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
        row.setOwnerUserId(owner); row.setAgentSessionId(session); row.setStatus("READY");
        row.setNormalizedInputJson(input); row.setStateSnapshotJson(snapshot);
        row.setStateDigest(AssayEntryStateService.digest(snapshot)); row.setPublicPayloadJson(stateService.json(payload));
        row.setCreatedAt(now()); row.setUpdatedAt(row.getCreatedAt()); row.setExpiresAt(row.getCreatedAt().plusMinutes(5));
        row.setContentDigest(contentDigest(row));
        previews.supersedeActive(session, owner);
        if (previews.insert(row) != 1) throw new IllegalStateException("保存化验预览失败");
        return view(row);
    }

    public AssayEntryArchivedPreviewVO load(Integer owner, String session, String ref) {
        access.requireCurrent(owner, session);
        return view(requireReady(owner, session, ref));
    }

    public void cancel(Integer owner, String session, String ref) {
        access.requireCurrent(owner, session);
        AgentAssayEntryPreview row = owned(owner, session, ref);
        if ("CANCELED".equals(row.getStatus())) return;
        if (!"READY".equals(row.getStatus()) && !"CONFIRMED".equals(row.getStatus())) throw invalid();
        if (previews.updateStatus(row.getId(), "CANCELED") != 1) throw invalid();
    }

    /** Used by the future confirmation/execution boundary inside the same session-locked transaction. */
    public AssayEntryStateService.State verifyUnchanged(Integer owner, String session, String ref) {
        return verifyForExecution(owner, session, ref).state();
    }

    public VerifiedPreview verifyForExecution(Integer owner, String session, String ref) {
        access.requireCurrent(owner, session);
        AgentAssayEntryPreview row = loadOwnedControl(owner, session, ref);
        if ((!"READY".equals(row.getStatus()) && !"CONFIRMED".equals(row.getStatus()))
                || row.getExpiresAt() == null || !row.getExpiresAt().isAfter(now())) throw invalid();
        try {
            var stored = objectMapper.readTree(row.getNormalizedInputJson());
            if (stored.has("entryKind")) {
                if (!"GROUP_V1".equals(stored.path("entryKind").asText())) throw invalid();
                var request = objectMapper.treeToValue(stored.path("request"), AssayGroupEntryPreviewDTO.class);
                AssayGroupEntryStateService.GroupState current;
                try { current = groupStates.lockAndRead(request); }
                catch (BusinessException changed) {
                    // A previously valid group becoming empty/deleted/malformed or losing a product also
                    // invalidates confirmation. Do not leave the UI offering retries of the stale preview.
                    throw new BusinessException(ErrorCode.ASSAY_ENTRY_STATE_CHANGED);
                }
                if (!current.digest().equals(row.getStateDigest())) throw new BusinessException(ErrorCode.ASSAY_ENTRY_STATE_CHANGED);
                var entries = new ArrayList<VerifiedEntry>();
                for (int index = 0; index < current.inputs().size(); index++)
                    entries.add(new VerifiedEntry(current.inputs().get(index), current.products().get(index)));
                return new VerifiedPreview(row, List.copyOf(entries), current.groupId(), current.groupName());
            }
            AssaySubmitDTO input = objectMapper.readValue(row.getNormalizedInputJson(), AssaySubmitDTO.class);
            var current = stateService.lockAndRead(input);
            if (!current.digest().equals(row.getStateDigest())) throw new BusinessException(ErrorCode.ASSAY_ENTRY_STATE_CHANGED);
            return new VerifiedPreview(row, input, current);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw invalid(); }
    }

    private AgentAssayEntryPreview requireReady(Integer owner, String session, String ref) {
        AgentAssayEntryPreview row = loadOwnedControl(owner, session, ref);
        if (!"READY".equals(row.getStatus()) || row.getExpiresAt() == null || !row.getExpiresAt().isAfter(now())) throw invalid();
        return row;
    }

    /** Terminal result lookup must remain possible after preview expiry, but never after ownership/access loss. */
    public AgentAssayEntryPreview loadOwnedControl(Integer owner, String session, String ref) {
        access.requireCurrent(owner, session);
        return checkedOwned(owner, session, ref);
    }

    public AgentAssayEntryPreview loadOwnedRecovery(Integer owner, String session, String ref) {
        access.requireRecovery(owner, session);
        return checkedOwned(owner, session, ref);
    }

    private AgentAssayEntryPreview checkedOwned(Integer owner, String session, String ref) {
        AgentAssayEntryPreview row = owned(owner, session, ref);
        if (row.getStateSnapshotJson() == null || row.getNormalizedInputJson() == null || row.getPublicPayloadJson() == null
                || !Objects.equals(row.getStateDigest(), AssayEntryStateService.digest(row.getStateSnapshotJson()))
                || !Objects.equals(row.getContentDigest(), contentDigest(row))) throw invalid();
        return row;
    }

    public record VerifiedEntry(AssaySubmitDTO input, AssayEntryStateService.State state) { }
    public record VerifiedPreview(AgentAssayEntryPreview row, List<VerifiedEntry> entries, Integer groupId, String groupName) {
        public VerifiedPreview(AgentAssayEntryPreview row, AssaySubmitDTO input, AssayEntryStateService.State state) {
            this(row, List.of(new VerifiedEntry(input, state)), null, null);
        }
        public boolean isGroup() { return groupId != null; }
        public AssaySubmitDTO input() { requireSingle(); return entries.get(0).input(); }
        public AssayEntryStateService.State state() { requireSingle(); return entries.get(0).state(); }
        private void requireSingle() { if (isGroup() || entries.size() != 1) throw invalid(); }
    }

    private AgentAssayEntryPreview owned(Integer owner, String session, String ref) {
        if (ref == null || !ref.matches("aep1_[A-Za-z0-9_-]{43}")) throw invalid();
        AgentAssayEntryPreview row = previews.selectForUpdate(ref);
        if (row == null || !Objects.equals(owner, row.getOwnerUserId()) || !Objects.equals(session, row.getAgentSessionId())) throw invalid();
        return row;
    }

    private AssayEntryArchivedPreviewVO view(AgentAssayEntryPreview row) {
        try { return new AssayEntryArchivedPreviewVO(row.getPreviewRef(), "待确认", row.getExpiresAt(), objectMapper.readTree(row.getPublicPayloadJson())); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw invalid(); }
    }

    private static String contentDigest(AgentAssayEntryPreview row) {
        return AssayEntryStateService.digest(row.getOwnerUserId() + "\n" + row.getAgentSessionId() + "\n"
                + row.getPreviewRef() + "\n" + row.getExpiresAt() + "\n" + row.getNormalizedInputJson() + "\n" + row.getStateSnapshotJson() + "\n" + row.getPublicPayloadJson());
    }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.ASSAY_ENTRY_PREVIEW_INVALID); }
}
