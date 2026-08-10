package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionNoopOrchestrator;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionTokenDTO;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionControlResultVO;
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
@RequestMapping("/api/agent/sessions/{agentSessionId}/finish-inbound-execution/s2-noop")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.finish-inbound.s2-noop-enabled", havingValue = "true")
public class FinishInboundExecutionS2NoopController {
    private final AgentSessionService agentSessionService;
    private final FinishInboundExecutionNoopOrchestrator orchestrator;

    @PostMapping("/confirmations/{confirmationRef}/consume")
    @PreAuthorize("hasAuthority('task:view') and hasAuthority('task:confirm')")
    public Result<FinishInboundExecutionControlResultVO> consume(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String agentSessionId,
            @PathVariable String confirmationRef,
            @Valid @RequestBody FinishInboundExecutionTokenDTO request) {
        AgentAccessPolicy.requireAdmin(loginUser);
        agentSessionService.requireOwnedActiveSession(loginUser, agentSessionId);
        Set<String> authorities = loginUser.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
        return Result.success(orchestrator.consume(confirmationRef, request.getExecutionToken(),
                request.getIdempotencyKey(), loginUser.getUser(), agentSessionId, authorities));
    }
}
