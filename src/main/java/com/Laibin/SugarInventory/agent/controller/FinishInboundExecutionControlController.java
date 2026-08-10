package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionConfirmationService;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionConfirmationDTO;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionConfirmationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent/sessions/{agentSessionId}/finish-inbound-execution")
@RequiredArgsConstructor
public class FinishInboundExecutionControlController {
    private final AgentSessionService agentSessionService;
    private final FinishInboundExecutionConfirmationService confirmationService;

    @PostMapping("/confirmations")
    @PreAuthorize("hasAuthority('task:view') and hasAuthority('task:confirm')")
    public Result<FinishInboundExecutionConfirmationVO> confirm(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String agentSessionId,
            @Valid @RequestBody FinishInboundExecutionConfirmationDTO request) {
        requireSession(loginUser, agentSessionId);
        return Result.success(confirmationService.confirm(request.getPreviewRef(), loginUser.getUser(),
                agentSessionId, authorities(loginUser)));
    }

    @PostMapping("/confirmations/{confirmationRef}/revoke")
    @PreAuthorize("hasAuthority('task:view') and hasAuthority('task:confirm')")
    public Result<FinishInboundExecutionConfirmationVO> revoke(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String agentSessionId,
            @PathVariable String confirmationRef) {
        requireSession(loginUser, agentSessionId);
        return Result.success(confirmationService.revoke(confirmationRef, loginUser.getUser(),
                agentSessionId, authorities(loginUser)));
    }

    private void requireSession(LoginUser loginUser, String agentSessionId) {
        AgentAccessPolicy.requireAdmin(loginUser);
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
    }

    private static Set<String> authorities(LoginUser loginUser) {
        return loginUser.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
    }
}
