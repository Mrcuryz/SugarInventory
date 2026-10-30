package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.service.AssayEntryArchiveService;
import com.Laibin.SugarInventory.agent.service.AssayEntryExecutionService;
import com.Laibin.SugarInventory.agent.service.AssayEntryExecutionFacade;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AssayEntryExecutionTokenDTO;
import com.Laibin.SugarInventory.domain.dto.AssayEntryPreviewDTO;
import com.Laibin.SugarInventory.domain.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Human-operated control endpoints. These paths are intentionally absent from MCP delegation allowlists. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/sessions/{sessionId}/assay-entry")
@ConditionalOnProperty(name = "agent.assay-entry.execute-enabled", havingValue = "true")
@PreAuthorize("hasAuthority('assay:view') and hasAnyAuthority('assay:create', 'quality:test')")
public class AssayEntryExecutionController {
    private final AssayEntryArchiveService archive;
    private final AssayEntryExecutionService execution;
    private final AssayEntryExecutionFacade facade;
    private final com.Laibin.SugarInventory.agent.service.AssayEntryReferenceService references;

    @PostMapping("/previews/from-product")
    public Result<AssayEntryArchivedPreviewVO> previewFromProduct(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
            @Valid @RequestBody com.Laibin.SugarInventory.domain.dto.AssayEntryReferencePreviewDTO request) {
        Integer owner = owner(user);
        return Result.success(archive.create(owner, sessionId, references.resolve(owner, request)));
    }

    @PostMapping("/previews")
    public Result<AssayEntryArchivedPreviewVO> preview(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
                                                       @Valid @RequestBody AssayEntryPreviewDTO request) {
        return Result.success(archive.create(owner(user), sessionId, request));
    }
    @PostMapping("/previews/group")
    public Result<AssayEntryArchivedPreviewVO> previewGroup(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
            @Valid @RequestBody com.Laibin.SugarInventory.domain.dto.AssayGroupEntryPreviewDTO request) {
        return Result.success(archive.createGroup(owner(user), sessionId, request));
    }
    @PostMapping("/previews/from-group")
    public Result<AssayEntryArchivedPreviewVO> previewFromGroup(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId,
            @Valid @RequestBody com.Laibin.SugarInventory.domain.dto.AssayGroupEntryReferencePreviewDTO request) {
        Integer owner = owner(user);
        return Result.success(archive.createGroup(owner, sessionId, references.resolveGroup(owner, request)));
    }
    @PostMapping("/previews/{ref}/confirm")
    public Result<AssayEntryConfirmationVO> confirm(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId, @PathVariable String ref) {
        return Result.success(facade.confirm(owner(user), sessionId, ref));
    }
    @PostMapping("/previews/{ref}/execute")
    public Result<AssayEntryExecutionResultVO> execute(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId, @PathVariable String ref,
                                                     @Valid @RequestBody AssayEntryExecutionTokenDTO request) {
        return Result.success(facade.execute(owner(user), sessionId, ref, request));
    }
    @GetMapping("/previews/{ref}/result")
    public Result<AssayEntryExecutionResultVO> result(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId, @PathVariable String ref) {
        return Result.success(execution.result(owner(user), sessionId, ref));
    }
    @PostMapping("/previews/{ref}/cancel")
    public Result<Void> cancel(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId, @PathVariable String ref) {
        execution.cancel(owner(user), sessionId, ref); return Result.success(null);
    }
    @PostMapping("/previews/{ref}/recover")
    public Result<AssayEntryExecutionResultVO> recover(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId, @PathVariable String ref) {
        return Result.success(execution.recover(owner(user), sessionId, ref));
    }
    private Integer owner(LoginUser user) { AgentAccessPolicy.requireAgentAccess(user); return user.getUser().getId(); }
}
