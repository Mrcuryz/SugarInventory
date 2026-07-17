package com.Laibin.SugarInventory.agent.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AuditAgentQueries;
import com.Laibin.SugarInventory.agent.service.AuditAgentReadService;
import com.Laibin.SugarInventory.agent.vo.AuditAgentVO;
import com.Laibin.SugarInventory.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/agent-read")
@RequiredArgsConstructor
public class AuditAgentReadController {
    private final AuditAgentReadService service;

    @PostMapping("/operation-logs/query")
    @PreAuthorize("hasAuthority('log:view')")
    public Result<AuditAgentVO.PageResult<AuditAgentVO.OperationLogRow>> searchOperationLogs(@RequestBody(required = false) AuditAgentQueries.OperationLogs query) {
        return Result.success(service.searchOperationLogs(query));
    }

    @PostMapping("/agent-tool-audit/query")
    @PreAuthorize("hasAuthority('agent:audit:view')")
    public Result<AuditAgentVO.PageResult<AuditAgentVO.ToolAuditRow>> queryAgentToolAudit(@RequestBody(required = false) AuditAgentQueries.ToolAudit query) {
        return Result.success(service.queryAgentToolAudit(query));
    }

    @PostMapping("/agent-answer-reviews/query")
    @PreAuthorize("hasAuthority('agent:review:view')")
    public Result<AuditAgentVO.PageResult<AuditAgentVO.AnswerReviewRow>> queryAgentAnswerReviews(@AuthenticationPrincipal LoginUser loginUser,
                                                                                                @RequestBody(required = false) AuditAgentQueries.AnswerReviews query) {
        return Result.success(service.queryAgentAnswerReviews(loginUser, query));
    }
}
