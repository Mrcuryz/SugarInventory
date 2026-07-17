package com.Laibin.SugarInventory.agent.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewQueryDTO;
import com.Laibin.SugarInventory.agent.dto.AuditAgentQueries;
import com.Laibin.SugarInventory.agent.service.AgentMessageReviewService;
import com.Laibin.SugarInventory.agent.service.AuditAgentReadService;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewListVO;
import com.Laibin.SugarInventory.agent.vo.AuditAgentVO;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.OperationLogQueryDTO;
import com.Laibin.SugarInventory.domain.po.AgentToolAuditLog;
import com.Laibin.SugarInventory.domain.po.OperationLog;
import com.Laibin.SugarInventory.mapper.AgentToolAuditLogMapper;
import com.Laibin.SugarInventory.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditAgentReadServiceImpl implements AuditAgentReadService {
    private final OperationLogService operationLogService;
    private final AgentToolAuditLogMapper toolAuditLogMapper;
    private final AgentMessageReviewService reviewService;
    private final ObjectMapper objectMapper;

    @Override
    public AuditAgentVO.PageResult<AuditAgentVO.OperationLogRow> searchOperationLogs(AuditAgentQueries.OperationLogs query) {
        AuditAgentQueries.OperationLogs source = query == null ? new AuditAgentQueries.OperationLogs() : query;
        int page = page(source.getPage()), size = size(source.getSize()); validateRange(source.getStartTime(), source.getEndTime());
        OperationLogQueryDTO criteria = new OperationLogQueryDTO();
        criteria.setTableName(text(source.getModule(), 100, "module")); criteria.setOperationType(text(source.getOperationType(), 20, "operationType"));
        criteria.setOperator(text(source.getOperator(), 100, "operator")); criteria.setStartTime(source.getStartTime()); criteria.setEndTime(source.getEndTime());
        criteria.setPage(page); criteria.setSize(size);
        PageResult<OperationLog> result = operationLogService.queryOperationLogs(criteria);
        List<AuditAgentVO.OperationLogRow> rows = safe(result == null ? null : result.getRecords()).stream().map(item ->
                AuditAgentVO.OperationLogRow.builder().module(item.getTableName()).operationType(item.getOperationType())
                        .operator(item.getOperator()).operationTime(item.getOperationTime()).changedFieldNames(fieldNames(item.getChangedFields())).build()).toList();
        return auditPage("RECORDED_BUSINESS_OPERATION_LOGS", result == null ? 0 : result.getTotal(), page, size, rows,
                List.of("仅返回发生变更的字段名称，不返回 oldData、字段值、请求正文、凭据或原始异常。",
                        "操作日志只覆盖系统已登记的审计事件，不能据此断言未登记操作从未发生。"));
    }

    @Override
    public AuditAgentVO.PageResult<AuditAgentVO.ToolAuditRow> queryAgentToolAudit(AuditAgentQueries.ToolAudit query) {
        AuditAgentQueries.ToolAudit source = query == null ? new AuditAgentQueries.ToolAudit() : query;
        int page = page(source.getPage()), size = size(source.getSize()); validateRange(source.getStartTime(), source.getEndTime());
        LambdaQueryWrapper<AgentToolAuditLog> wrapper = new LambdaQueryWrapper<AgentToolAuditLog>()
                .eq(has(source.getCapability()), AgentToolAuditLog::getToolName, text(source.getCapability(), 100, "capability"))
                .eq(has(source.getResultCode()), AgentToolAuditLog::getResultCode, text(source.getResultCode(), 40, "resultCode"))
                .eq(has(source.getErrorCode()), AgentToolAuditLog::getErrorCode, text(source.getErrorCode(), 80, "errorCode"))
                .ge(source.getStartTime() != null, AgentToolAuditLog::getCreatedAt, source.getStartTime())
                .le(source.getEndTime() != null, AgentToolAuditLog::getCreatedAt, source.getEndTime())
                .orderByDesc(AgentToolAuditLog::getCreatedAt).orderByDesc(AgentToolAuditLog::getId);
        Page<AgentToolAuditLog> result = toolAuditLogMapper.selectPage(new Page<>(page, size), wrapper);
        List<AuditAgentVO.ToolAuditRow> rows = result.getRecords().stream().map(item -> AuditAgentVO.ToolAuditRow.builder()
                .capability(item.getToolName()).resultCode(item.getResultCode()).errorCode(item.getErrorCode())
                .durationMs(item.getDurationMs()).occurredAt(item.getCreatedAt()).build()).toList();
        return auditPage("RECORDED_AGENT_TOOL_AUDIT", result.getTotal(), page, size, rows,
                List.of("不返回会话/用户/消息/调用内部 ID、上游路径、参数、请求响应摘要、Prompt、模型上下文、密钥或原始堆栈。",
                        "工具审计只表示已记录调用及结果类别，不等同于业务数据正确性结论。"));
    }

    @Override
    public AuditAgentVO.PageResult<AuditAgentVO.AnswerReviewRow> queryAgentAnswerReviews(LoginUser loginUser, AuditAgentQueries.AnswerReviews query) {
        AuditAgentQueries.AnswerReviews source = query == null ? new AuditAgentQueries.AnswerReviews() : query;
        int page = page(source.getPage()), size = size(source.getSize());
        AgentMessageReviewQueryDTO criteria = new AgentMessageReviewQueryDTO(); criteria.setPage(page); criteria.setSize(size);
        criteria.setReviewStatus(text(source.getReviewStatus(), 40, "reviewStatus")); criteria.setAnswerStatus(text(source.getAnswerStatus(), 40, "answerStatus"));
        criteria.setFailureDomain(text(source.getFailureDomain(), 80, "failureDomain")); criteria.setFailureCategory(text(source.getFailureCategory(), 120, "failureCategory"));
        criteria.setSuggestedFixType(text(source.getSuggestedFixType(), 80, "suggestedFixType")); criteria.setTestCaseStatus(text(source.getTestCaseStatus(), 40, "testCaseStatus"));
        criteria.setPriorityOnly(source.getPriorityOnly());
        PageResult<AgentMessageReviewListVO> result = reviewService.pageReviews(loginUser, criteria);
        List<AuditAgentVO.AnswerReviewRow> rows = safe(result == null ? null : result.getRecords()).stream().map(item ->
                AuditAgentVO.AnswerReviewRow.builder().createdAt(item.getCreatedAt()).answerStatus(item.getAnswerStatus())
                        .confidenceLevel(item.getConfidenceLevel()).failureDomain(item.getFailureDomain()).failureCategory(item.getFailureCategory())
                        .suggestedFixType(item.getSuggestedFixType()).intentType(item.getIntentType()).reviewStatus(item.getReviewStatus())
                        .testCaseStatus(item.getTestCaseStatus()).build()).toList();
        return auditPage("AGENT_ANSWER_REVIEW_SAFE_SUMMARY", result == null ? 0 : result.getTotal(), page, size, rows,
                List.of("不返回用户原问题、助手原回答、工具名称、决策快照、证据正文、会话/消息/用户内部 ID 或审核人身份。",
                        "Review 状态是质量治理记录，不是业务事实或自动处罚依据。"));
    }

    private List<String> fieldNames(String json) {
        if (!has(json)) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json); if (!node.isObject()) return List.of();
            List<String> result = new ArrayList<>(); Iterator<String> names = node.fieldNames();
            while (names.hasNext() && result.size() < 50) { String name = names.next(); if (name.matches("[A-Za-z0-9_]{1,80}")) result.add(name); }
            return result;
        } catch (Exception ignored) { return List.of(); }
    }
    private <T> AuditAgentVO.PageResult<T> auditPage(String scope, long total, int page, int size, List<T> records, List<String> limitations) {
        return AuditAgentVO.PageResult.<T>builder().dataScope(scope).total(total).page(page).size(size).records(records).limitations(limitations).build();
    }
    private void validateRange(LocalDateTime start, LocalDateTime end) { if (start != null && end != null && start.isAfter(end)) throw new BusinessException(400, "startTime 不能晚于 endTime"); }
    private int page(Integer value) { if (value == null) return 1; if (value < 1) throw new BusinessException(400, "page 必须大于 0"); return value; }
    private int size(Integer value) { int result = value == null ? 20 : value; if (result < 1 || result > 50) throw new BusinessException(400, "size 必须在 1 到 50 之间"); return result; }
    private String text(String value, int max, String field) { if (!has(value)) return null; String result = value.trim(); if (result.length() > max) throw new BusinessException(400, field + " 长度超限"); return result; }
    private boolean has(String value) { return value != null && !value.isBlank(); }
    private <T> List<T> safe(List<T> rows) { return rows == null ? List.of() : rows; }
}
