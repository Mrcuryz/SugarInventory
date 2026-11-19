package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.productimport.ProductImportCandidates;
import com.Laibin.SugarInventory.agent.security.AgentSecurityContext;
import com.Laibin.SugarInventory.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="agent.product-import", name={"upload-enabled","matching-enabled"}, havingValue="true")
public class ProductImportCandidateController {
    public record Query(@NotBlank @Pattern(regexp="[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") String attachment,
                        @NotNull @Min(2) @Max(501) Integer rowNumber) {}
    private final ProductImportCandidates candidates;
    @PostMapping("/api/master-data/agent-read/product-import/candidates")
    @PreAuthorize("hasAuthority('product:view') and (hasAuthority('product:create') or hasAuthority('product:update'))")
    public Result<ProductImportCandidates.Evidence> read(@AuthenticationPrincipal LoginUser user,
            @RequestAttribute(AgentSecurityContext.ATTR_AGENT_SESSION_ID) String session, @Valid @RequestBody Query query) {
        return Result.success(candidates.read(user,session,query.attachment(),query.rowNumber()));
    }
}
