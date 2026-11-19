package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.productimport.ProductImportAccessService;
import com.Laibin.SugarInventory.agent.productimport.ProductImportWorkbookReader;
import com.Laibin.SugarInventory.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Report deployed capabilities, rather than offering an unavailable upload action in the UI. */
@RestController
public class ProductImportCapabilitiesController {
    private final ProductImportAccessService access;
    private final boolean enabled;
    private final boolean matching;

    public ProductImportCapabilitiesController(ProductImportAccessService access,
            @Value("${agent.product-import.upload-enabled:false}") boolean enabled,
            @Value("${agent.product-import.matching-enabled:false}") boolean matching) {
        this.access = access;
        this.enabled = enabled;
        this.matching = matching;
    }

    public record Capabilities(boolean uploadEnabled, boolean matchingEnabled, boolean executionEnabled,
                               int maxBytes, int maxRows) {}

    @GetMapping("/api/agent/sessions/{sessionId}/product-import/capabilities")
    @PreAuthorize("hasAuthority('product:view') and (hasAuthority('product:create') or hasAuthority('product:update'))")
    public Result<Capabilities> capabilities(@AuthenticationPrincipal LoginUser user, @PathVariable String sessionId) {
        access.requireCurrent(user, sessionId);
        return Result.success(new Capabilities(enabled, enabled && matching, false,
                ProductImportWorkbookReader.MAX_BYTES, ProductImportWorkbookReader.MAX_ROWS));
    }
}
