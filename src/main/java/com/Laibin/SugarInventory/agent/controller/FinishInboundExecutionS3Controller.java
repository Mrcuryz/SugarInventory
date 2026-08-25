package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionConfirmationService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionDomainOrchestrator;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionPendingService;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPendingQueryDTO;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionTokenDTO;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionConfirmationVO;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPendingVO;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@RequestMapping("/api/agent/sessions/{agentSessionId}/finish-inbound-execution/s3")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.finish-inbound.s3-execute-enabled", havingValue = "true")
public class FinishInboundExecutionS3Controller {
    private final AgentSessionService agentSessionService;
    private final FinishInboundExecutionPendingService pendingService;
    private final FinishInboundExecutionConfirmationService confirmationService;
    private final FinishInboundExecutionDomainOrchestrator orchestrator;

    @PostMapping("/pending-preview")
    @PreAuthorize("hasAuthority('task:view') "
            + "and hasAuthority('agent:finish-inbound:execute')")
    public Result<FinishInboundExecutionPendingVO> pendingPreview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String agentSessionId,
            @Valid @RequestBody FinishInboundExecutionPendingQueryDTO request) {
        requireSession(loginUser, agentSessionId);
        return Result.success(pendingService.load(request.getPalletCodes(), loginUser.getUser(),
                agentSessionId, authorities(loginUser)));
    }

    @PostMapping("/previews/{previewRef}/confirm-and-execute")
    @PreAuthorize("hasAuthority('task:view') "
            + "and hasAuthority('agent:finish-inbound:execute')")
    public Result<FinishInboundExecutionResultVO> confirmAndExecute(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String agentSessionId,
            @PathVariable String previewRef) {
        requireSession(loginUser, agentSessionId);
        Set<String> authorities = authorities(loginUser);
        FinishInboundExecutionConfirmationVO confirmation =
                confirmationService.confirmForDomainExecution(previewRef, loginUser.getUser(),
                        agentSessionId, authorities);
        return Result.success(orchestrator.execute(confirmation.getConfirmationRef(),
                confirmation.getExecutionToken(), confirmation.getIdempotencyKey(),
                loginUser.getUser(), agentSessionId, authorities));
    }

    @PostMapping("/confirmations/{confirmationRef}/execute")
    @PreAuthorize("hasAuthority('task:view') "
            + "and hasAuthority('agent:finish-inbound:execute')")
    public Result<FinishInboundExecutionResultVO> execute(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String agentSessionId,
            @PathVariable String confirmationRef,
            @Valid @RequestBody FinishInboundExecutionTokenDTO request) {
        requireSession(loginUser, agentSessionId);
        Set<String> authorities = authorities(loginUser);
        return Result.success(orchestrator.execute(confirmationRef, request.getExecutionToken(),
                request.getIdempotencyKey(), loginUser.getUser(), agentSessionId, authorities));
    }

    private void requireSession(LoginUser loginUser, String agentSessionId) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
    }

    private static Set<String> authorities(LoginUser loginUser) {
        return loginUser.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
    }
}
