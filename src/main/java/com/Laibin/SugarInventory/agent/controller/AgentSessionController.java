package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.context.AgentConversationMemory;
import com.Laibin.SugarInventory.agent.dto.AgentInterruptResumeRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageRequestDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.agent.dto.AgentSessionCreateDTO;
import com.Laibin.SugarInventory.agent.dto.AgentSessionRevokeDTO;
import com.Laibin.SugarInventory.agent.dto.AgentToolAuditDTO;
import com.Laibin.SugarInventory.agent.gateway.AgentGatewayService;
import com.Laibin.SugarInventory.agent.mcp.McpSessionManager;
import com.Laibin.SugarInventory.agent.security.AgentAccessPolicy;
import com.Laibin.SugarInventory.agent.security.AgentSecurityContext;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.AgentMcpWarmupService;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.vo.AgentMessageResponseVO;
import com.Laibin.SugarInventory.agent.vo.AgentSessionVO;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentSessionController {
    private final AgentSessionService agentSessionService;
    private final AgentGatewayService agentGatewayService;
    private final McpSessionManager mcpSessionManager;
    private final AgentConversationMemory conversationMemory;
    private final AgentMessageReviewService agentMessageReviewService;
    private final AgentMcpWarmupService mcpWarmupService;

    public AgentSessionController(AgentSessionService agentSessionService,
                                  AgentGatewayService agentGatewayService,
                                  McpSessionManager mcpSessionManager,
                                  AgentConversationMemory conversationMemory,
                                  AgentMessageReviewService agentMessageReviewService,
                                  AgentMcpWarmupService mcpWarmupService) {
        this.agentSessionService = agentSessionService;
        this.agentGatewayService = agentGatewayService;
        this.mcpSessionManager = mcpSessionManager;
        this.conversationMemory = conversationMemory;
        this.agentMessageReviewService = agentMessageReviewService;
        this.mcpWarmupService = mcpWarmupService;
    }

    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(@AuthenticationPrincipal LoginUser loginUser,
                                                @Valid @RequestBody(required = false) AgentSessionCreateDTO dto,
                                                HttpServletRequest request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        AgentSessionVO session = agentSessionService.createSession(loginUser, dto, request);
        mcpWarmupService.warmUp(loginUser, session.getAgentSessionId());
        return Result.success(session);
    }

    @GetMapping("/sessions/current")
    public Result<List<AgentSessionVO>> currentSessions(@AuthenticationPrincipal LoginUser loginUser) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        return Result.success(agentSessionService.listCurrentSessions(loginUser));
    }

    @PostMapping("/sessions/{agentSessionId}/messages")
    public Result<AgentMessageResponseVO> sendMessage(@AuthenticationPrincipal LoginUser loginUser,
                                                      @PathVariable String agentSessionId,
                                                      @Valid @RequestBody AgentMessageRequestDTO request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        return Result.success(agentGatewayService.handleMessage(loginUser, agentSessionId, request));
    }

    @PostMapping(value = "/sessions/{agentSessionId}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable String agentSessionId,
                                    @Valid @RequestBody AgentMessageRequestDTO request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        return agentGatewayService.streamMessage(loginUser, agentSessionId, request);
    }

    @PostMapping("/sessions/{agentSessionId}/messages/{messageId}/cancel")
    public Result<Boolean> cancelMessage(@AuthenticationPrincipal LoginUser loginUser,
                                         @PathVariable String agentSessionId,
                                         @PathVariable String messageId) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        return Result.success(agentGatewayService.cancelMessage(loginUser, agentSessionId, messageId));
    }

    @PostMapping("/sessions/{agentSessionId}/message-reviews")
    public Result<Boolean> recordMessageReview(@AuthenticationPrincipal LoginUser loginUser,
                                               @PathVariable String agentSessionId,
                                               @Valid @RequestBody AgentMessageReviewRecordDTO request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        agentMessageReviewService.recordAssistantTurn(loginUser, agentSessionId, request);
        return Result.success(Boolean.TRUE);
    }

    @PostMapping("/sessions/{agentSessionId}/message-reviews/{messageId}/feedback")
    public Result<Boolean> submitMessageReviewFeedback(@AuthenticationPrincipal LoginUser loginUser,
                                                       @PathVariable String agentSessionId,
                                                       @PathVariable String messageId,
                                                       @Valid @RequestBody AgentMessageReviewFeedbackDTO request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        agentMessageReviewService.submitFeedback(loginUser, agentSessionId, messageId, request);
        return Result.success(Boolean.TRUE);
    }

    @PostMapping("/sessions/{agentSessionId}/interrupts/{interruptId}/resume")
    public Result<AgentMessageResponseVO> resumeInterrupt(@AuthenticationPrincipal LoginUser loginUser,
                                                          @PathVariable String agentSessionId,
                                                          @PathVariable String interruptId,
                                                          @Valid @RequestBody AgentInterruptResumeRequestDTO request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        return Result.success(agentGatewayService.handleMessage(
                loginUser,
                agentSessionId,
                toResumeMessageRequest(interruptId, request)));
    }

    @PostMapping(value = "/sessions/{agentSessionId}/interrupts/{interruptId}/resume/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeInterruptStream(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable String agentSessionId,
                                            @PathVariable String interruptId,
                                            @Valid @RequestBody AgentInterruptResumeRequestDTO request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        return agentGatewayService.streamMessage(loginUser, agentSessionId, toResumeMessageRequest(interruptId, request));
    }

    @DeleteMapping("/sessions/{agentSessionId}")
    public Result<Boolean> revokeSession(@AuthenticationPrincipal LoginUser loginUser,
                                         @PathVariable String agentSessionId,
                                         @Valid @RequestBody(required = false) AgentSessionRevokeDTO dto) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        String reason = dto == null ? null : dto.getRevokedReason();
        agentSessionService.revokeSession(loginUser, agentSessionId, reason);
        mcpSessionManager.closeSession(agentSessionId);
        conversationMemory.clear(agentSessionId);
        agentGatewayService.clearSession(agentSessionId);
        return Result.success(Boolean.TRUE);
    }

    @PostMapping("/audit/tool-calls")
    public Result<Boolean> recordToolAudit(@AuthenticationPrincipal LoginUser loginUser,
                                           @Valid @RequestBody AgentToolAuditDTO dto,
                                           HttpServletRequest request) {
        AgentAccessPolicy.requireAgentAccess(loginUser);
        Object sessionId = request.getAttribute(AgentSecurityContext.ATTR_AGENT_SESSION_ID);
        if (sessionId == null) {
            throw new BusinessException(401, "Agent session is required.");
        }
        Object userId = request.getAttribute(AgentSecurityContext.ATTR_AGENT_USER_ID);
        Integer resolvedUserId = userId instanceof Integer value ? value : loginUser.getUser().getId();
        agentSessionService.recordToolAudit(String.valueOf(sessionId), resolvedUserId, dto);
        return Result.success(Boolean.TRUE);
    }

    private AgentMessageRequestDTO toResumeMessageRequest(String interruptId, AgentInterruptResumeRequestDTO request) {
        AgentMessageRequestDTO messageRequest = new AgentMessageRequestDTO();
        messageRequest.setMessage("用户选择了候选项");
        Map<String, Object> selectedOption = new LinkedHashMap<>();
        selectedOption.put("interruptId", interruptId);
        selectedOption.put("resumeToken", request.getResumeToken());
        selectedOption.put("action", request.getAction());
        selectedOption.put("clientRequestId", request.getClientRequestId());
        if (request.getSelection() != null) {
            selectedOption.put("optionId", request.getSelection().getOptionId());
            selectedOption.put("previewId", request.getSelection().getPreviewId());
        }
        messageRequest.setPageContext(Map.of("selectedOption", selectedOption));
        return messageRequest;
    }
}

