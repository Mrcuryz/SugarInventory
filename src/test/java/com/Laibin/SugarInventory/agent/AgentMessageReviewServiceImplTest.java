package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewFeedbackDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewQueryDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewRecordDTO;
import com.Laibin.SugarInventory.agent.dto.AgentMessageReviewStatusUpdateDTO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewDetailVO;
import com.Laibin.SugarInventory.agent.vo.AgentMessageReviewListVO;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.agent.service.impl.AgentMessageReviewServiceImpl;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.AgentMessageReview;
import com.Laibin.SugarInventory.domain.po.AgentMessageReviewEvidence;
import com.Laibin.SugarInventory.domain.po.AgentToolAuditLog;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.AgentInterruptStateMapper;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewEvidenceMapper;
import com.Laibin.SugarInventory.mapper.AgentMessageReviewMapper;
import com.Laibin.SugarInventory.mapper.AgentToolAuditLogMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMessageReviewServiceImplTest {
    private AgentMessageReviewMapper reviewMapper;
    private AgentMessageReviewEvidenceMapper evidenceMapper;
    private AgentToolAuditLogMapper toolAuditLogMapper;
    private AgentInterruptStateMapper interruptStateMapper;
    private AgentMessageReviewServiceImpl service;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        reviewMapper = mock(AgentMessageReviewMapper.class);
        evidenceMapper = mock(AgentMessageReviewEvidenceMapper.class);
        toolAuditLogMapper = mock(AgentToolAuditLogMapper.class);
        interruptStateMapper = mock(AgentInterruptStateMapper.class);
        AgentSessionService agentSessionService = mock(AgentSessionService.class);
        when(toolAuditLogMapper.selectList(any())).thenReturn(List.of());
        when(interruptStateMapper.selectList(any())).thenReturn(List.of());
        when(reviewMapper.insert(any())).thenAnswer(invocation -> {
            AgentMessageReview review = invocation.getArgument(0);
            review.setId(99L);
            return 1;
        });
        service = new AgentMessageReviewServiceImpl(
                reviewMapper,
                evidenceMapper,
                toolAuditLogMapper,
                interruptStateMapper,
                agentSessionService,
                new ObjectMapper());

        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        loginUser = new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));
    }

    @Test
    void recordAssistantTurnMarksBusinessAnswerWithoutToolAsLowConfidence() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-1");
        dto.setUserQuestion("8号库位的库存情况");
        dto.setAssistantAnswerTextSafe("未找到匹配库位，请换一个库位名称。");
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("LOW_CONFIDENCE");
        assertThat(review.getConfidenceLevel()).isEqualTo("LOW");
        assertThat(review.getFailureDomain()).isEqualTo("DATA");
        assertThat(review.getFailureCategory()).isEqualTo("NO_MATCH_OR_FALLBACK");
        assertThat(review.getAssistantAnswerTextSafe()).doesNotContain("Authorization", "Bearer ");
    }

    @Test
    void recordAssistantTurnStoresActualToolsForSuccessfulBusinessAnswer() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-2");
        dto.setUserQuestion("这些主要存放在哪些库位？");
        dto.setAssistantAnswerTextSafe("当前库存主要存放在以下库位。");
        dto.setActualToolNames(List.of("get_inventory_distribution", "get_inventory_distribution"));

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("COMPLETED");
        assertThat(review.getConfidenceLevel()).isEqualTo("MEDIUM");
        assertThat(review.getActualToolNames()).isEqualTo("get_inventory_distribution");
        assertThat(review.getAnswerTraceSummary()).contains("actualTools=get_inventory_distribution");
        assertThat(review.getAgentDecisionSnapshot()).contains("\"actual_tools\":[\"get_inventory_distribution\"]");
    }

    @Test
    void recordAssistantTurnClassifiesResolverOnlyAuditAsPlannerIssue() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentToolAuditLog audit = new AgentToolAuditLog();
        audit.setToolName("resolve_warehouses");
        audit.setResultCode("SUCCESS");
        audit.setDurationMs(12L);
        when(toolAuditLogMapper.selectList(any())).thenReturn(List.of(audit));

        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-resolver");
        dto.setUserQuestion("8号库位的库存情况");
        dto.setAssistantAnswerTextSafe("已找到 8号库位。");
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("LOW_CONFIDENCE");
        assertThat(review.getFailureDomain()).isEqualTo("PLANNER");
        assertThat(review.getFailureCategory()).isEqualTo("RESOLVER_ONLY");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_PLANNER");
        assertThat(review.getActualToolNames()).isEqualTo("resolve_warehouses");
    }

    @Test
    void recordAssistantTurnClassifiesNonEmptyToolResultRenderedAsEmpty() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentToolAuditLog audit = new AgentToolAuditLog();
        audit.setToolName("get_inventory_distribution");
        audit.setResultCode("SUCCESS");
        audit.setResponseSummary("{\"groupCount\":2,\"totalStockText\":\"2板3件\"}");
        when(toolAuditLogMapper.selectList(any())).thenReturn(List.of(audit));

        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-safe-adapter");
        dto.setUserQuestion("8号库位有哪些库存？");
        dto.setAssistantAnswerTextSafe("没有查询到库存。");
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> reviewCaptor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        AgentMessageReview review = reviewCaptor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getFailureDomain()).isEqualTo("SAFE_ADAPTER");
        assertThat(review.getFailureCategory()).isEqualTo("NON_EMPTY_RESULT_RENDERED_AS_EMPTY");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_SAFE_ADAPTER");

        ArgumentCaptor<AgentMessageReviewEvidence> evidenceCaptor = ArgumentCaptor.forClass(AgentMessageReviewEvidence.class);
        verify(evidenceMapper, org.mockito.Mockito.atLeastOnce()).insert(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues())
                .anySatisfy(evidence -> assertThat(evidence.getEvidenceSummary()).contains("主查询工具结果摘要显示非空"));
    }

    @Test
    void recordAssistantTurnStoresFrontendIntentRouterSnapshotIntoReviewEvidence() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        AgentToolAuditLog resolverAudit = new AgentToolAuditLog();
        resolverAudit.setToolName("resolve_warehouses");
        resolverAudit.setResultCode("SUCCESS");
        AgentToolAuditLog distributionAudit = new AgentToolAuditLog();
        distributionAudit.setToolName("get_inventory_distribution");
        distributionAudit.setResultCode("SUCCESS");
        distributionAudit.setResponseSummary("{\"groupCount\":1,\"totalStockText\":\"3板20件\"}");
        when(toolAuditLogMapper.selectList(any())).thenReturn(List.of(resolverAudit, distributionAudit));

        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-router");
        dto.setUserQuestion("8号库位有什么？");
        dto.setAssistantAnswerTextSafe("8号库位当前有黄冰糖（袋）3板20件。");
        dto.setIntentType("data_query");
        dto.setIntentSubtype("warehouse_inventory_contents");
        dto.setBusinessDomain("inventory");
        dto.setBusinessObjects(Map.of("warehouse", "8号库位", "warehouseId", 8, "token", "secret"));
        dto.setMissingSlots(List.of());
        dto.setSupportStatus("supported");
        dto.setNextAction("call_tool");
        dto.setPlannedTools(List.of("resolve_warehouses", "get_inventory_distribution"));
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> reviewCaptor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        AgentMessageReview review = reviewCaptor.getValue();
        assertThat(review.getAnswerTraceSummary()).contains(
                "intentType=data_query",
                "intentSubtype=warehouse_inventory_contents",
                "plannedTools=resolve_warehouses,get_inventory_distribution",
                "actualTools=resolve_warehouses,get_inventory_distribution");
        assertThat(review.getAgentDecisionSnapshot()).contains(
                "\"intent_type\":\"data_query\"",
                "\"business_domain\":\"inventory\"",
                "\"planned_tools\":[\"resolve_warehouses\",\"get_inventory_distribution\"]",
                "\"actual_tools\":[\"resolve_warehouses\",\"get_inventory_distribution\"]");
        assertThat(review.getAgentDecisionSnapshot()).contains("\"warehouse\":\"8号库位\"");
        assertThat(review.getAgentDecisionSnapshot()).doesNotContain("warehouseId", "token", "secret");

        ArgumentCaptor<AgentMessageReviewEvidence> evidenceCaptor = ArgumentCaptor.forClass(AgentMessageReviewEvidence.class);
        verify(evidenceMapper, org.mockito.Mockito.atLeastOnce()).insert(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues())
                .anySatisfy(evidence -> {
                    assertThat(evidence.getEvidenceType()).isEqualTo("INTENT_ROUTER");
                    assertThat(evidence.getEvidenceSummary()).contains(
                            "intent_type=data_query",
                            "planned_tools=resolve_warehouses,get_inventory_distribution",
                            "actual_tools=resolve_warehouses,get_inventory_distribution");
                    assertThat(evidence.getEvidenceSummary()).doesNotContain("warehouseId", "token", "secret");
                });
    }

    @Test
    void recordAssistantTurnKeepsWriteOperationRouterSnapshotWithoutBusinessSlotFallback() {
        when(reviewMapper.selectOne(any())).thenReturn(null);

        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-write-operation");
        dto.setUserQuestion("帮我出库");
        dto.setAssistantAnswerTextSafe("我现在不能直接替你创建或执行出库操作。你可以先告诉我产品、托盘码或库位，我可以帮你做出库前检查。");
        dto.setAnswerStatus("COMPLETED");
        dto.setIntentType("write_operation");
        dto.setIntentSubtype("outbound");
        dto.setBusinessDomain("outbound");
        dto.setSupportStatus("unsupported");
        dto.setNextAction("explain_unsupported");
        dto.setPlannedTools(List.of());
        dto.setMissingSlots(List.of());
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> reviewCaptor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        AgentMessageReview review = reviewCaptor.getValue();
        assertThat(review.getAnswerTraceSummary()).contains(
                "intentType=write_operation",
                "intentSubtype=outbound",
                "businessDomain=outbound",
                "supportStatus=unsupported",
                "nextAction=explain_unsupported",
                "plannedTools=-",
                "actualTools=-");
        assertThat(review.getAgentDecisionSnapshot()).contains(
                "\"intent_type\":\"write_operation\"",
                "\"business_domain\":\"outbound\"",
                "\"support_status\":\"unsupported\"",
                "\"next_action\":\"explain_unsupported\"",
                "\"planned_tools\":[]",
                "\"actual_tools\":[]",
                "\"missing_slots\":[]");
        assertThat(review.getAgentDecisionSnapshot()).doesNotContain("COMPLETED", "PALLET_CODE");
    }

    @Test
    void recordAssistantTurnClassifiesMissingCapabilitySeparatelyFromNoMatch() {
        when(reviewMapper.selectOne(any())).thenReturn(null);

        AgentMessageReviewRecordDTO dto = new AgentMessageReviewRecordDTO();
        dto.setMessageId("msg-missing-capability");
        dto.setUserQuestion("最近有没有化验异常？");
        dto.setAssistantAnswerTextSafe("我现在还不能直接汇总最近的化验异常，因为化验异常分析工具还没接入。你可以先按产品、库位或托盘查询库存和无化验风险。");
        dto.setIntentType("report_analysis");
        dto.setIntentSubtype("assay_analysis_tool_missing");
        dto.setBusinessDomain("assay");
        dto.setSupportStatus("unsupported");
        dto.setNextAction("explain_unsupported");
        dto.setPlannedTools(List.of());
        dto.setMissingSlots(List.of());
        dto.setFinishReason("completed");

        service.recordAssistantTurn(loginUser, "session-1", dto);

        ArgumentCaptor<AgentMessageReview> reviewCaptor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).insert(reviewCaptor.capture());
        AgentMessageReview review = reviewCaptor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getFailureDomain()).isEqualTo("PLANNER");
        assertThat(review.getFailureCategory()).isEqualTo("CAPABILITY_UNSUPPORTED");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_PLANNER");
        assertThat(review.getAnswerTraceSummary()).contains(
                "intentType=report_analysis",
                "intentSubtype=assay_analysis_tool_missing",
                "supportStatus=unsupported",
                "nextAction=explain_unsupported",
                "plannedTools=-",
                "actualTools=-");
        assertThat(review.getAgentDecisionSnapshot()).contains(
                "\"intent_type\":\"report_analysis\"",
                "\"support_status\":\"unsupported\"",
                "\"next_action\":\"explain_unsupported\"",
                "\"planned_tools\":[]",
                "\"actual_tools\":[]");
        assertThat(review.getFailureCategory()).isNotEqualTo("NO_MATCH_OR_FALLBACK");
    }

    @Test
    void submitFeedbackClassifiesWrongIntentAsPlannerReviewAndRegressionNeeded() {
        AgentMessageReview existing = new AgentMessageReview();
        existing.setId(12L);
        existing.setAgentSessionId("session-1");
        existing.setMessageId("msg-3");
        existing.setAnswerStatus("COMPLETED");
        when(reviewMapper.selectOne(any())).thenReturn(existing);

        AgentMessageReviewFeedbackDTO dto = new AgentMessageReviewFeedbackDTO();
        dto.setFeedbackType("WRONG_INTENT");
        dto.setExpectedIntentSummary("查询全部产品最近90天不合格库存，按产品分组");
        dto.setExpectedToolNames(List.of("get_inventory_distribution"));

        service.submitFeedback(loginUser, "session-1", "msg-3", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).updateById(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getFailureDomain()).isEqualTo("PLANNER");
        assertThat(review.getFailureCategory()).isEqualTo("INTENT_MISS");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_PLANNER");
        assertThat(review.getTestCaseStatus()).isEqualTo("NEEDED");
        assertThat(review.getExpectedToolNames()).isEqualTo("get_inventory_distribution");
    }

    @Test
    void submitFeedbackClassifiesAutoDetectedUserCorrection() {
        AgentMessageReview existing = new AgentMessageReview();
        existing.setId(13L);
        existing.setAgentSessionId("session-1");
        existing.setMessageId("msg-4");
        when(reviewMapper.selectOne(any())).thenReturn(existing);

        AgentMessageReviewFeedbackDTO dto = new AgentMessageReviewFeedbackDTO();
        dto.setFeedbackType("USER_CORRECTION");
        dto.setFeedbackNote("用户下一轮纠正：不是这个，我问的是全部产品");
        dto.setExpectedIntentSummary("不是这个，我问的是全部产品");

        service.submitFeedback(loginUser, "session-1", "msg-4", dto);

        ArgumentCaptor<AgentMessageReview> captor = ArgumentCaptor.forClass(AgentMessageReview.class);
        verify(reviewMapper).updateById(captor.capture());
        AgentMessageReview review = captor.getValue();
        assertThat(review.getAnswerStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(review.getFailureDomain()).isEqualTo("USER_INPUT");
        assertThat(review.getFailureCategory()).isEqualTo("USER_CORRECTION_DETECTED");
        assertThat(review.getSuggestedFixType()).isEqualTo("FIX_PLANNER");
        assertThat(review.getTestCaseStatus()).isEqualTo("NEEDED");
    }

    @Test
    void pageReviewsReturnsSafeLiteRowsWithIntentAndTools() {
        AgentMessageReview review = reviewRecord();
        when(reviewMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AgentMessageReview> page = invocation.getArgument(0);
            page.setRecords(List.of(review));
            page.setTotal(1);
            return page;
        });

        AgentMessageReviewQueryDTO query = new AgentMessageReviewQueryDTO();
        query.setReviewStatus("OPEN");
        query.setAnswerStatus("NEEDS_REVIEW");

        PageResult<AgentMessageReviewListVO> result = service.pageReviews(adminUser(), query);

        assertThat(result.getTotal()).isEqualTo(1);
        AgentMessageReviewListVO row = result.getRecords().get(0);
        assertThat(row.getUserQuestionSummary()).contains("8号库位有什么");
        assertThat(row.getAssistantAnswerSummary()).contains("没有查询到库存");
        assertThat(row.getIntentType()).isEqualTo("data_query");
        assertThat(row.getPlannedTools()).containsExactly("resolve_warehouses", "get_inventory_distribution");
        assertThat(row.getActualToolNames()).containsExactly("resolve_warehouses");
        assertThat(row.toString()).doesNotContain("warehouseId", "secret", "Authorization", "stackTrace");
    }

    @Test
    void pageReviewsRequiresDedicatedReadPermissionOutsideAdminRole() {
        User user = new User();
        user.setId(2);
        user.setRoleCode("STAFF");
        LoginUser staffWithoutPermission = new LoginUser(
                user,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))
        );

        assertThatThrownBy(() -> service.pageReviews(
                staffWithoutPermission,
                new AgentMessageReviewQueryDTO()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void pageReviewsAllowsDedicatedReadPermissionWithoutUpdatePermission() {
        User user = new User();
        user.setId(3);
        user.setRoleCode("STAFF");
        LoginUser staffWithViewPermission = new LoginUser(
                user,
                List.of(new SimpleGrantedAuthority("agent:review:view"))
        );
        when(reviewMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AgentMessageReview> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        PageResult<AgentMessageReviewListVO> result = service.pageReviews(
                staffWithViewPermission,
                new AgentMessageReviewQueryDTO()
        );

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void getReviewDetailReturnsEvidenceAndSanitizedDecisionSnapshot() {
        AgentMessageReview review = reviewRecord();
        AgentMessageReviewEvidence evidence = new AgentMessageReviewEvidence();
        evidence.setId(5L);
        evidence.setReviewId(review.getId());
        evidence.setEvidenceType("INTENT_ROUTER");
        evidence.setRefId("msg-review");
        evidence.setEvidenceSummary("intent_type=data_query；Authorization: Bearer should-redact；token=secret");
        when(reviewMapper.selectById(31L)).thenReturn(review);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));

        AgentMessageReviewDetailVO detail = service.getReviewDetail(adminUser(), 31L);

        assertThat(detail.getAgentDecisionSnapshot()).containsEntry("intent_type", "data_query");
        assertThat(detail.getAgentDecisionSnapshot().toString()).doesNotContain("warehouseId", "secret", "Authorization");
        assertThat(detail.getEvidenceSummary()).hasSize(1);
        assertThat(detail.getEvidenceSummary().get(0).getEvidenceSummary())
                .contains("[AUTH_REDACTED]")
                .contains("[SECRET_REDACTED]")
                .doesNotContain("Authorization", "Bearer", "token", "secret");
    }

    @Test
    void getReviewDetailRepairsLegacyUnknownWriteOperationSnapshotForDisplay() {
        AgentMessageReview review = reviewRecord();
        review.setUserQuestion("帮我出库");
        review.setAnswerTraceSummary("intentType=unknown；businessDomain=GENERAL；supportStatus=COMPLETED；missing_slots=PALLET_CODE");
        review.setAgentDecisionSnapshot("{\"intent_type\":\"unknown\",\"business_domain\":\"GENERAL\",\"support_status\":\"COMPLETED\",\"missing_slots\":[\"PALLET_CODE\"],\"planned_tools\":[\"UNKNOWN\"]}");
        review.setActualToolNames(null);
        when(reviewMapper.selectById(31L)).thenReturn(review);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());

        AgentMessageReviewDetailVO detail = service.getReviewDetail(adminUser(), 31L);

        assertThat(detail.getAnswerTraceSummary()).contains(
                "intentType=write_operation",
                "intentSubtype=outbound",
                "businessDomain=outbound",
                "supportStatus=unsupported",
                "nextAction=explain_unsupported");
        assertThat(detail.getAgentDecisionSnapshot()).containsEntry("intent_type", "write_operation");
        assertThat(detail.getAgentDecisionSnapshot()).containsEntry("business_domain", "outbound");
        assertThat(detail.getAgentDecisionSnapshot()).containsEntry("support_status", "unsupported");
        assertThat(detail.getAgentDecisionSnapshot()).containsEntry("next_action", "explain_unsupported");
        assertThat(detail.getAgentDecisionSnapshot().get("missing_slots")).isEqualTo(List.of());
        assertThat(detail.getPlannedTools()).isEmpty();
        assertThat(detail.getActualToolNames()).isEmpty();
    }

    @Test
    void updateReviewStatusSupportsTriageFixedWontFixAndTestNeeded() {
        AgentMessageReview review = reviewRecord();
        when(reviewMapper.selectById(31L)).thenReturn(review);
        when(evidenceMapper.selectList(any())).thenReturn(List.of());

        AgentMessageReviewStatusUpdateDTO triage = new AgentMessageReviewStatusUpdateDTO();
        triage.setReviewStatus("TRIAGED");
        triage.setAdminNote("已确认是 intent miss");
        service.updateReviewStatus(adminUser(), 31L, triage);
        assertThat(review.getReviewStatus()).isEqualTo("TRIAGED");
        assertThat(review.getReviewNote()).isEqualTo("已确认是 intent miss");

        AgentMessageReviewStatusUpdateDTO testNeeded = new AgentMessageReviewStatusUpdateDTO();
        testNeeded.setReviewStatus("TEST_NEEDED");
        service.updateReviewStatus(adminUser(), 31L, testNeeded);
        assertThat(review.getTestCaseStatus()).isEqualTo("NEEDED");

        AgentMessageReviewStatusUpdateDTO fixed = new AgentMessageReviewStatusUpdateDTO();
        fixed.setReviewStatus("FIXED");
        service.updateReviewStatus(adminUser(), 31L, fixed);
        assertThat(review.getReviewStatus()).isEqualTo("FIXED");

        AgentMessageReviewStatusUpdateDTO wontFix = new AgentMessageReviewStatusUpdateDTO();
        wontFix.setReviewStatus("WONT_FIX");
        service.updateReviewStatus(adminUser(), 31L, wontFix);
        assertThat(review.getReviewStatus()).isEqualTo("WONT_FIX");
    }

    private LoginUser adminUser() {
        User user = new User();
        user.setId(1);
        user.setRoleCode("ADMIN");
        return new LoginUser(user, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private AgentMessageReview reviewRecord() {
        AgentMessageReview review = new AgentMessageReview();
        review.setId(31L);
        review.setAgentSessionId("session-review");
        review.setMessageId("msg-review");
        review.setUserQuestion("8号库位有什么？ Authorization: Bearer secret");
        review.setAssistantAnswerTextSafe("没有查询到库存。stackTrace=hidden");
        review.setAssistantAnswerSummary("没有查询到库存。");
        review.setAnswerTraceSummary("intentType=data_query；nextAction=call_tool");
        review.setAgentDecisionSnapshot("{\"intent_type\":\"data_query\",\"planned_tools\":[\"resolve_warehouses\",\"get_inventory_distribution\"],\"actual_tools\":[\"resolve_warehouses\"],\"business_objects\":{\"warehouse\":\"8号库位\",\"warehouseId\":8,\"token\":\"secret\"}}");
        review.setActualToolNames("resolve_warehouses");
        review.setAnswerStatus("NEEDS_REVIEW");
        review.setConfidenceLevel("LOW");
        review.setFailureDomain("PLANNER");
        review.setFailureCategory("INTENT_MISS");
        review.setSuggestedFixType("FIX_PLANNER");
        review.setReviewStatus("OPEN");
        review.setTestCaseStatus("NEEDED");
        return review;
    }
}
