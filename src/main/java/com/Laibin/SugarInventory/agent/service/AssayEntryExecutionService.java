package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.agent.security.AssayEntryControlCodec;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.AssayEntryExecutionTokenDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.AgentAssayEntryConfirmation;
import com.Laibin.SugarInventory.domain.po.AgentAssayEntryPreview;
import com.Laibin.SugarInventory.domain.vo.AssayEntryConfirmationVO;
import com.Laibin.SugarInventory.domain.vo.AssayEntryExecutionResultVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.AssayWriteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static com.Laibin.SugarInventory.agent.service.AssayEntryStateService.digest;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.REPEATABLE_READ)
public class AssayEntryExecutionService {
    private final AssayEntryArchiveService archive;
    private final AgentAssayEntryConfirmationMapper confirmations;
    private final AgentAssayEntryPreviewMapper previews;
    private final AgentAssayEntryAuditMapper audit;
    private final AssayEntryControlCodec codec;
    private final AssayWriteService writer;
    private final ObjectMapper json;
    @Value("${agent.assay-entry.execute-enabled:false}")
    private boolean enabled;

    public AssayEntryConfirmationVO confirm(Integer owner, String session, String ref) {
        requireEnabled();
        AgentAssayEntryPreview preview = archive.loadOwnedControl(owner, session, ref);
        AgentAssayEntryConfirmation existing = confirmations.selectByPreviewForUpdate(ref);
        if (existing != null) {
            requireBinding(existing, preview);
            if (!("SUCCEEDED".equals(existing.getStatus()) && "SUCCEEDED".equals(preview.getStatus()))) {
                requireConfirmed(existing, preview);
                archive.verifyForExecution(owner, session, ref);
            }
            var credentials = codec.credentials(existing);
            if (!codec.matches(credentials.executionToken(), existing.getTokenDigest())
                    || !codec.matches(credentials.idempotencyKey(), existing.getIdempotencyDigest())) throw invalid();
            return confirmation(existing, credentials, true);
        }
        if (!"READY".equals(preview.getStatus())) throw invalid();
        archive.verifyForExecution(owner, session, ref);
        AgentAssayEntryConfirmation row = new AgentAssayEntryConfirmation();
        row.setConfirmationRef(codec.newRef()); row.setPreviewRef(ref);
        row.setOwnerUserId(owner); row.setAgentSessionId(session); row.setContentDigest(preview.getContentDigest());
        row.setConfirmedAt(now());
        row.setExpiresAt(preview.getExpiresAt().isBefore(row.getConfirmedAt().plusMinutes(2))
                ? preview.getExpiresAt() : row.getConfirmedAt().plusMinutes(2));
        row.setStatus("CONFIRMED");
        var credentials = codec.credentials(row);
        row.setTokenDigest(digest(credentials.executionToken())); row.setIdempotencyDigest(digest(credentials.idempotencyKey()));
        if (confirmations.insert(row) != 1 || previews.updateStatus(preview.getId(), "CONFIRMED") != 1) throw invalid();
        appendAudit(owner, session, ref, "USER_CONFIRMED", null);
        return confirmation(row, credentials, false);
    }

    public AssayEntryExecutionResultVO execute(Integer owner, String session, String ref, AssayEntryExecutionTokenDTO request) {
        requireEnabled();
        if (request == null || request.isUnsupportedFields() || request.getExecutionToken() == null || request.getIdempotencyKey() == null
                || !request.getExecutionToken().matches("aet1_[A-Za-z0-9_-]{43}")
                || !request.getIdempotencyKey().matches("aei1_[A-Za-z0-9_-]{43}")) throw invalid();
        AgentAssayEntryPreview preview = archive.loadOwnedControl(owner, session, ref);
        AgentAssayEntryConfirmation row = confirmations.selectByPreviewForUpdate(ref);
        requireBinding(row, preview);
        var expectedCredentials = codec.credentials(row);
        if (!codec.matches(request.getExecutionToken(), row.getTokenDigest())
                || !codec.matches(request.getIdempotencyKey(), row.getIdempotencyDigest())
                || !codec.matches(expectedCredentials.executionToken(), row.getTokenDigest())
                || !codec.matches(expectedCredentials.idempotencyKey(), row.getIdempotencyDigest())) throw invalid();
        if ("SUCCEEDED".equals(row.getStatus()) && "SUCCEEDED".equals(preview.getStatus())) return storedResult(row, preview, true);
        requireConfirmed(row, preview);
        var verified = archive.verifyForExecution(owner, session, ref);
        if (verified.isGroup()) return executeGroup(owner, session, ref, preview, row, verified);
        var assay = writer.appendVerified(verified.input(), owner, verified.state().existingVersion(), verified.state().judgement());
        if (assay.getId() == null || assay.getId() <= 0) throw new IllegalStateException("化验保存结果缺少记录标识");
        ObjectNode details = readPayload(preview.getPublicPayloadJson());
        details.put("previewOnly", false); details.put("saved", true); details.put("recordVersion", assay.getVersion());
        var warnings = details.withArray("warnings");
        if (!warnings.isEmpty()) warnings.remove(0); // Replace only the unsaved notice; retain quality warnings.
        warnings.insert(0, "化验记录已保存。");
        var result = new AssayEntryExecutionResultVO(true, false, "已保存", assay.getId(), assay.getVersion(),
                verified.state().product().getProductName(), assay.getSampleDate(), assay.getCreatedAt(), details);
        row.setStatus("SUCCEEDED"); row.setAssayId(assay.getId()); row.setCompletedAt(now()); row.setResultJson(writeJson(result));
        if (confirmations.updateById(row) != 1 || previews.updateStatus(preview.getId(), "SUCCEEDED") != 1) {
            throw new IllegalStateException("保存化验执行结果失败");
        }
        appendAudit(owner, session, ref, "EXECUTION_SUCCEEDED", assay.getId());
        return result;
    }

    private AssayEntryExecutionResultVO executeGroup(Integer owner, String session, String ref, AgentAssayEntryPreview preview,
            AgentAssayEntryConfirmation row, AssayEntryArchiveService.VerifiedPreview verified) {
        ObjectNode details = readPayload(preview.getPublicPayloadJson());
        var products = details.path("products");
        if (!"GROUP_V1".equals(details.path("entryKind").asText()) || !products.isArray()
                || verified.entries().isEmpty() || products.size() != verified.entries().size()
                || details.path("productCount").asInt() != products.size()
                || details.path("groupId").asInt() != verified.groupId()) throw invalid();
        // Validate every target before the first insert; inputs and states came from the locked server archive.
        for (int index = 0; index < verified.entries().size(); index++) {
            if (!(products.get(index) instanceof ObjectNode)
                    || products.get(index).path("productId").asInt() != verified.entries().get(index).input().getProductId()) throw invalid();
        }
        LocalDateTime savedAt = null;
        for (int index = 0; index < verified.entries().size(); index++) {
            var entry = verified.entries().get(index);
            var assay = writer.appendVerified(entry.input(), owner, entry.state().existingVersion(), entry.state().judgement());
            if (assay.getId() == null || assay.getId() <= 0 || assay.getVersion() == null || assay.getVersion() <= 0)
                throw new IllegalStateException("化验保存结果缺少记录标识或版本");
            ObjectNode product = (ObjectNode) products.get(index);
            product.put("assayId", assay.getId()); product.put("recordVersion", assay.getVersion());
            product.put("previewOnly", false); product.put("saved", true);
            var warnings = product.withArray("warnings");
            if (!warnings.isEmpty()) warnings.remove(0);
            warnings.insert(0, "化验记录已保存。");
            savedAt = assay.getCreatedAt();
            appendAudit(owner, session, ref, "EXECUTION_SUCCEEDED", assay.getId());
        }
        details.put("previewOnly", false); details.put("saved", true);
        details.putArray("warnings").add("本组全部产品的化验记录已保存。");
        // A group has no singular assay ID/version. Every actual result is retained in details.products.
        var result = new AssayEntryExecutionResultVO(true, false, "已保存", null, null, null,
                verified.entries().get(0).input().getSampleDate(), savedAt, details);
        row.setStatus("SUCCEEDED"); row.setAssayId(null); row.setCompletedAt(now()); row.setResultJson(writeJson(result));
        if (confirmations.updateById(row) != 1 || previews.updateStatus(preview.getId(), "SUCCEEDED") != 1)
            throw new IllegalStateException("保存化验组执行结果失败");
        appendAudit(owner, session, ref, "GROUP_EXECUTION_SUCCEEDED", null);
        return result;
    }

    public AssayEntryExecutionResultVO result(Integer owner, String session, String ref) {
        AgentAssayEntryPreview preview = archive.loadOwnedControl(owner, session, ref);
        return resultOf(preview, ref);
    }

    /** Atomic settle: an earlier execute wins and is read back, or recovery cancels before a late execute can run. */
    public AssayEntryExecutionResultVO recover(Integer owner, String session, String ref) {
        AgentAssayEntryPreview preview = archive.loadOwnedRecovery(owner, session, ref);
        AssayEntryExecutionResultVO result = resultOf(preview, ref);
        if (result.saved()) return result;
        if ("READY".equals(preview.getStatus()) || "CONFIRMED".equals(preview.getStatus())) {
            if (previews.updateStatus(preview.getId(), "CANCELED") != 1) throw invalid();
            appendAudit(owner, session, ref, "RECOVERY_CANCELED", null);
            return new AssayEntryExecutionResultVO(false, false, "已取消", null, null, null, null, null, null);
        }
        return result;
    }

    private AssayEntryExecutionResultVO resultOf(AgentAssayEntryPreview preview, String ref) {
        AgentAssayEntryConfirmation row = confirmations.selectByPreviewForUpdate(ref);
        if (row != null) {
            requireBinding(row, preview);
            if ("SUCCEEDED".equals(row.getStatus()) && "SUCCEEDED".equals(preview.getStatus())) return storedResult(row, preview, true);
        }
        if ("SUCCEEDED".equals(preview.getStatus()) || (row != null && "SUCCEEDED".equals(row.getStatus()))) {
            throw new IllegalStateException("化验执行状态不一致，请核查保存结果");
        }
        String label = switch (preview.getStatus()) {
            case "CANCELED" -> "已取消";
            case "SUPERSEDED" -> "已失效";
            default -> preview.getExpiresAt().isAfter(now()) ? "尚未保存" : "已过期";
        };
        return new AssayEntryExecutionResultVO(false, false, label, null, null, null, null, null, null);
    }

    public void cancel(Integer owner, String session, String ref) {
        archive.cancel(owner, session, ref);
        appendAudit(owner, session, ref, "USER_CANCELED", null);
    }

    private void requireBinding(AgentAssayEntryConfirmation row, AgentAssayEntryPreview preview) {
        if (row == null || !Objects.equals(row.getPreviewRef(), preview.getPreviewRef())
                || !Objects.equals(row.getOwnerUserId(), preview.getOwnerUserId())
                || !Objects.equals(row.getAgentSessionId(), preview.getAgentSessionId())
                || !Objects.equals(row.getContentDigest(), preview.getContentDigest())) throw invalid();
    }
    private void requireConfirmed(AgentAssayEntryConfirmation row, AgentAssayEntryPreview preview) {
        if (!"CONFIRMED".equals(row.getStatus()) || !"CONFIRMED".equals(preview.getStatus())
                || row.getExpiresAt() == null || !row.getExpiresAt().isAfter(now())
                || preview.getExpiresAt() == null || !preview.getExpiresAt().isAfter(now())) throw invalid();
    }
    private AssayEntryConfirmationVO confirmation(AgentAssayEntryConfirmation row, AssayEntryControlCodec.Credentials credentials, boolean replay) {
        return new AssayEntryConfirmationVO(row.getPreviewRef(), credentials.executionToken(), credentials.idempotencyKey(), row.getExpiresAt(), replay);
    }
    private AssayEntryExecutionResultVO storedResult(AgentAssayEntryConfirmation row, AgentAssayEntryPreview preview, boolean replay) {
        try {
            var result = json.readValue(row.getResultJson(), AssayEntryExecutionResultVO.class);
            if (!result.saved() || !Objects.equals(result.assayId(), row.getAssayId())) throw new IllegalStateException("化验结果记录不一致");
            var original = readPayload(preview.getPublicPayloadJson());
            if ("GROUP_V1".equals(original.path("entryKind").asText())) validateGroupResult(result, original);
            else if (result.assayId() == null || result.assayId() <= 0 || result.version() == null || result.version() <= 0)
                throw new IllegalStateException("化验结果缺少记录标识或版本");
            return new AssayEntryExecutionResultVO(true, replay, result.statusLabel(), result.assayId(), result.version(),
                    result.productName(), result.sampleDate(), result.savedAt(), result.details());
        } catch (JsonProcessingException exception) { throw new IllegalStateException("读取化验保存结果失败", exception); }
    }
    private void validateGroupResult(AssayEntryExecutionResultVO result, ObjectNode original) {
        var details = result.details();
        if (result.assayId() != null || result.version() != null || details == null
                || !"GROUP_V1".equals(details.path("entryKind").asText())
                || !details.path("saved").asBoolean() || details.path("previewOnly").asBoolean(true)
                || !details.path("groupId").equals(original.path("groupId"))) throw invalidGroupResult();
        var products = details.path("products"); var expected = original.path("products");
        if (!products.isArray() || !expected.isArray() || products.isEmpty() || products.size() != expected.size()
                || details.path("productCount").asInt() != products.size()) throw invalidGroupResult();
        var ids = new java.util.HashSet<Integer>();
        for (int index = 0; index < products.size(); index++) {
            var product = products.get(index); var target = expected.get(index);
            if (!product.path("saved").asBoolean() || product.path("previewOnly").asBoolean(true)
                    || product.path("assayId").asInt() <= 0 || !ids.add(product.path("assayId").asInt())
                    || product.path("recordVersion").asInt() <= 0
                    || !product.path("recordVersion").equals(target.path("proposedVersion"))
                    || !product.path("productId").equals(target.path("productId"))
                    || !product.path("sampleDate").equals(target.path("sampleDate"))) throw invalidGroupResult();
        }
    }
    private IllegalStateException invalidGroupResult() { return new IllegalStateException("化验组保存结果不完整或与预览不一致，请核查"); }
    private ObjectNode readPayload(String value) {
        try { return (ObjectNode) json.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("读取化验预览失败", exception); }
    }
    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("生成化验保存结果失败", exception); }
    }
    private void appendAudit(Integer owner, String session, String ref, String event, Integer assayId) {
        if (audit.append(owner, session, ref, event, 200, assayId) != 1) throw new IllegalStateException("保存化验执行审计失败");
    }
    private void requireEnabled() { if (!enabled) throw new BusinessException(ErrorCode.ASSAY_ENTRY_EXECUTION_DISABLED); }
    private static BusinessException invalid() { return new BusinessException(ErrorCode.ASSAY_ENTRY_CONFIRMATION_INVALID); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MICROS); }
}
