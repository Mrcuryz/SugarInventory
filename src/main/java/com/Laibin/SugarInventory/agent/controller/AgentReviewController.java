package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewQueryDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewStatusUpdateDTO;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewDetailVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewListVO;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/agent/reviews")
public class AgentReviewController {
    private final AgentMessageReviewService agentMessageReviewService;

    public AgentReviewController(AgentMessageReviewService agentMessageReviewService) {
        this.agentMessageReviewService = agentMessageReviewService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('agent:review:view')")
    public Result<PageResult<AgentMessageReviewListVO>> pageReviews(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @ModelAttribute AgentMessageReviewQueryDTO query) {
        return Result.success(agentMessageReviewService.pageReviews(loginUser, query));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('agent:review:view')")
    public Result<AgentMessageReviewDetailVO> getReviewDetail(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id) {
        return Result.success(agentMessageReviewService.getReviewDetail(loginUser, id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('agent:review:update')")
    public Result<AgentMessageReviewDetailVO> updateReviewStatus(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long id,
            @Valid @RequestBody AgentMessageReviewStatusUpdateDTO request) {
        return Result.success(agentMessageReviewService.updateReviewStatus(loginUser, id, request));
    }
}
