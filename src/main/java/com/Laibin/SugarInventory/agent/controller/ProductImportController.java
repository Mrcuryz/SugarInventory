package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.productimport.ProductImportAccessService;
import com.Laibin.SugarInventory.agent.productimport.ProductImportDraftStore;
import com.Laibin.SugarInventory.agent.productimport.ProductImportUploadService;
import com.Laibin.SugarInventory.agent.productimport.ProductImportWorkbookReader;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Human attachment endpoints only. No business writes and not exposed in the model tool allowlist. */
@RestController
@RequestMapping("/api/agent/sessions/{sessionId}/product-import")
@ConditionalOnProperty(prefix = "agent.product-import", name = "upload-enabled", havingValue = "true")
@PreAuthorize("hasAuthority('product:view') and (hasAuthority('product:create') or hasAuthority('product:update'))")
@RequiredArgsConstructor
public class ProductImportController {
    private final ProductImportUploadService uploads;
    private final ProductImportAccessService access;
    private final ProductImportDraftStore drafts;
    private final ObjectMapper json;

    public record Receipt(String reference, String fileName, String sha256, String sheet, int rowCount, long expiresAt) {
        static Receipt of(ProductImportDraftStore.Draft draft) {
            return new Receipt(draft.reference(), draft.fileName(), draft.sha256(), draft.rows().get(0).sheet(),
                    draft.rows().size(), draft.expiresAt());
        }
    }
    public record SourcePage(Receipt attachment, int page, int pageSize, List<ProductImportWorkbookReader.SourceRow> rows) {}

    @GetMapping("/drafts")
    public Result<List<Receipt>> list(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId) {
        return Result.success(drafts.list(access.requireCurrent(user, sessionId), sessionId).stream().map(Receipt::of).toList());
    }

    @PostMapping(value = "/inspect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<ProductImportWorkbookReader.SheetInfo>> inspect(@AuthenticationPrincipal LoginUser user,
            @PathVariable String sessionId, @RequestParam("file") MultipartFile file) {
        return Result.success(uploads.inspect(user, sessionId, file));
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Receipt> upload(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) String sheet,
            @RequestParam(defaultValue = "{}") String mapping) {
        return Result.success(Receipt.of(uploads.upload(user, sessionId, file, sheet, parseMapping(mapping))));
    }

    @GetMapping("/drafts/{reference}")
    public Result<SourcePage> read(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
            @PathVariable String reference, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        Integer owner = access.requireCurrent(user, sessionId);
        if (page < 1 || page > ProductImportWorkbookReader.MAX_ROWS || pageSize < 1 || pageSize > 50)
            throw new BusinessException(400, "分页条件无效，每页最多50行");
        var draft = drafts.get(owner, sessionId, reference);
        int start = Math.min((page - 1) * pageSize, draft.rows().size());
        int end = Math.min(start + pageSize, draft.rows().size());
        return Result.success(new SourcePage(Receipt.of(draft), page, pageSize, draft.rows().subList(start, end)));
    }

    @DeleteMapping("/drafts/{reference}")
    public Result<Boolean> remove(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
                                 @PathVariable String reference) {
        drafts.delete(access.requireCurrent(user, sessionId), sessionId, reference);
        return Result.success(true);
    }

    private Map<Integer, String> parseMapping(String encoded) {
        if (encoded == null || encoded.length() > 4096) throw invalidMapping();
        try {
            JsonNode node = json.readerFor(JsonNode.class).with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(encoded);
            if (node == null || !node.isObject() || node.size() > 32) throw invalidMapping();
            Map<Integer, String> mapping = new LinkedHashMap<>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!entry.getKey().matches("0|[1-9][0-9]?") || !entry.getValue().isTextual()) throw invalidMapping();
                mapping.put(Integer.parseInt(entry.getKey()), entry.getValue().textValue());
            }
            return mapping;
        } catch (IOException e) { throw invalidMapping(); }
    }

    private BusinessException invalidMapping() { return new BusinessException(400, "列对应关系无效，请重新选择"); }
}
