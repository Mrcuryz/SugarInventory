package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.security.AgentSecurityContext;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionPreviewArchiveService;
import com.Laibin.SugarInventory.agent.service.FinishInboundExecutionPreviewService;
import com.Laibin.SugarInventory.agent.service.TaskTransitionPreviewArchiveService;
import com.Laibin.SugarInventory.controller.LogisticsAgentReadController;
import com.Laibin.SugarInventory.domain.dto.FinishInboundExecutionPreviewDTO;
import com.Laibin.SugarInventory.domain.dto.TaskTransitionPreviewDTO;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.FinishInboundExecutionPreviewVO;
import com.Laibin.SugarInventory.domain.vo.TaskTransitionPreviewVO;
import com.Laibin.SugarInventory.service.LogisticsAgentReadService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogisticsAgentReadControllerPreviewArchiveTest {

    @Test
    void forwardsAuthenticatedOwnerAgentSessionAndAuthoritiesToArchive() {
        LogisticsAgentReadService readService = mock(LogisticsAgentReadService.class);
        TaskTransitionPreviewArchiveService archiveService =
                mock(TaskTransitionPreviewArchiveService.class);
        FinishInboundExecutionPreviewService executionPreviewService =
                mock(FinishInboundExecutionPreviewService.class);
        FinishInboundExecutionPreviewArchiveService executionPreviewArchiveService =
                mock(FinishInboundExecutionPreviewArchiveService.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        LogisticsAgentReadController controller =
                new LogisticsAgentReadController(
                        readService,
                        archiveService,
                        executionPreviewService,
                        executionPreviewArchiveService);

        User user = new User();
        user.setId(7);
        LoginUser loginUser = new LoginUser(user, List.of(
                new SimpleGrantedAuthority("task:view"),
                new SimpleGrantedAuthority("task:confirm")));
        TaskTransitionPreviewDTO request = new TaskTransitionPreviewDTO();
        TaskTransitionPreviewVO preview = TaskTransitionPreviewVO.builder()
                .previewStatus("CONFLICT")
                .build();

        when(readService.previewTaskTransition(request, user)).thenReturn(preview);
        when(httpRequest.getAttribute(AgentSecurityContext.ATTR_AGENT_SESSION_ID))
                .thenReturn("agt-session-1");
        when(archiveService.persistReady(
                preview,
                7,
                "agt-session-1",
                Set.of("task:view", "task:confirm")))
                .thenReturn(preview);

        controller.previewTaskTransition(request, loginUser, httpRequest);

        verify(archiveService).persistReady(
                preview,
                7,
                "agt-session-1",
                Set.of("task:view", "task:confirm"));
    }

    @Test
    void exactExecutionPreviewUsesTheSameAuthenticatedSessionBinding() {
        LogisticsAgentReadService readService = mock(LogisticsAgentReadService.class);
        TaskTransitionPreviewArchiveService transitionArchiveService =
                mock(TaskTransitionPreviewArchiveService.class);
        FinishInboundExecutionPreviewService previewService =
                mock(FinishInboundExecutionPreviewService.class);
        FinishInboundExecutionPreviewArchiveService archiveService =
                mock(FinishInboundExecutionPreviewArchiveService.class);
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        LogisticsAgentReadController controller = new LogisticsAgentReadController(
                readService,
                transitionArchiveService,
                previewService,
                archiveService);
        User user = new User();
        user.setId(7);
        LoginUser loginUser = new LoginUser(user, List.of(
                new SimpleGrantedAuthority("task:view"),
                new SimpleGrantedAuthority("task:confirm")));
        FinishInboundExecutionPreviewDTO request = new FinishInboundExecutionPreviewDTO();
        FinishInboundExecutionPreviewVO preview = FinishInboundExecutionPreviewVO.builder()
                .previewStatus("CONFLICT")
                .build();
        when(previewService.preview(request, user)).thenReturn(preview);
        when(httpRequest.getAttribute(AgentSecurityContext.ATTR_AGENT_SESSION_ID))
                .thenReturn("agt-session-1");
        when(archiveService.persistReady(
                preview,
                7,
                "agt-session-1",
                Set.of("task:view", "task:confirm")))
                .thenReturn(preview);

        controller.previewFinishInboundExecution(request, loginUser, httpRequest);

        verify(archiveService).persistReady(
                preview,
                7,
                "agt-session-1",
                Set.of("task:view", "task:confirm"));
    }
}
