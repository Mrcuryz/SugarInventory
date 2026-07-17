package com.Laibin.SugarInventory.agent.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AuditAgentQueries;
import com.Laibin.SugarInventory.agent.vo.AuditAgentVO;

public interface AuditAgentReadService {
    AuditAgentVO.PageResult<AuditAgentVO.OperationLogRow> searchOperationLogs(AuditAgentQueries.OperationLogs query);
    AuditAgentVO.PageResult<AuditAgentVO.ToolAuditRow> queryAgentToolAudit(AuditAgentQueries.ToolAudit query);
    AuditAgentVO.PageResult<AuditAgentVO.AnswerReviewRow> queryAgentAnswerReviews(LoginUser loginUser, AuditAgentQueries.AnswerReviews query);
}
