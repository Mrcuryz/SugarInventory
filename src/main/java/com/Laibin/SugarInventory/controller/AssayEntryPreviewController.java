package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.agent.service.AssayEntryPreviewService;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.AssayEntryPreviewVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assay/entry")
@RequiredArgsConstructor
public class AssayEntryPreviewController {
    private final AssayEntryPreviewService previewService;
    private final com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService references;
    @org.springframework.beans.factory.annotation.Value("${agent.assay-entry.execute-enabled:false}")
    private boolean confirmationAvailable;

    @PostMapping("/preview")
    @Operation(summary = "单产品化验录入只读预览", description = "校验数值、已有版本及标准预判，不写入化验数据，不签发执行凭据")
    @PreAuthorize("hasAuthority('assay:view') and hasAnyAuthority('assay:create', 'quality:test')")
    public Result<AssayEntryPreviewVO> preview(@org.springframework.security.core.annotation.AuthenticationPrincipal com.Laibin.SugarInventory.SpringSecurity.LoginUser user,
                                              @Valid @RequestBody AssayEntryPreviewDTO request) {
        var preview = previewService.preview(request);
        return Result.success(preview == null ? null : preview.toBuilder().confirmationAvailable(confirmationAvailable)
                .productRef(confirmationAvailable ? references.encode(preview.getProductId(), user.getUser().getId()) : null).build());
    }
}
