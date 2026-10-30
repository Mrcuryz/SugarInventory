package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.agent.service.AssayGroupEntryPreviewService;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.AssayGroupEntryPreviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/assay/entry/group")
public class AssayGroupEntryPreviewController {
    private final AssayGroupEntryPreviewService service;
    private final com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService references;
    @org.springframework.beans.factory.annotation.Value("${agent.assay-entry.execute-enabled:false}")
    private boolean confirmationAvailable;
    @PostMapping("/from-reference/preview")
    @PreAuthorize("hasAuthority('assay:view') and hasAnyAuthority('assay:create', 'quality:test')")
    public Result<AssayGroupEntryPreviewVO> previewFromReference(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.Laibin.SugarInventory.SpringSecurity.LoginUser user,
            @Valid @RequestBody com.Laibin.SugarInventory.domain.dto.AssayGroupEntryReferencePreviewDTO request) {
        var result = service.preview(references.resolveGroup(user.getUser().getId(), request));
        // Echo the authenticated reference for transport identity checking, even when saving is disabled.
        return Result.success(new AssayGroupEntryPreviewVO(result.previewOnly(), confirmationAvailable,
                result.groupId(), result.groupName(), result.sampleDate(), result.productCount(), result.products(), result.warnings(),
                request.getGroupRef()));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('assay:view') and hasAnyAuthority('assay:create', 'quality:test')")
    public Result<AssayGroupEntryPreviewVO> preview(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.Laibin.SugarInventory.SpringSecurity.LoginUser user,
            @Valid @RequestBody AssayGroupEntryPreviewDTO request) {
        var result = service.preview(request);
        return Result.success(result == null ? null : new AssayGroupEntryPreviewVO(result.previewOnly(), confirmationAvailable,
                result.groupId(), result.groupName(), result.sampleDate(), result.productCount(), result.products(), result.warnings(),
                confirmationAvailable ? references.encodeGroup(result.groupId(), user.getUser().getId()) : null));
    }
}
