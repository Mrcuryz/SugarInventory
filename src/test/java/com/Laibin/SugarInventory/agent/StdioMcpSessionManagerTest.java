package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.mcp.McpProcessFactory;
import com.Laibin.SugarInventory.agent.mcp.McpSession;
import com.Laibin.SugarInventory.agent.mcp.StdioMcpSessionManager;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.Laibin.SugarInventory.domain.po.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StdioMcpSessionManagerTest {
    @Test
    void startsDedicatedProcessWithDelegatedTokenEnvironmentWithoutPuttingTokenInCommand() throws Exception {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        LoginUser loginUser = loginUser();
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(sessionService.issueDelegationTokenForInternalUse(loginUser, "session-1")).thenReturn("delegated.jwt.secret");
        when(sessionService.requireOwnedActiveSession(loginUser, "session-1")).thenReturn(session);

        AtomicReference<List<String>> commandRef = new AtomicReference<>();
        AtomicReference<Map<String, String>> envRef = new AtomicReference<>();
        McpProcessFactory factory = (command, environment) -> {
            commandRef.set(command);
            envRef.set(environment);
            return new FakeProcess();
        };
        StdioMcpSessionManager manager = new StdioMcpSessionManager(
                sessionService,
                factory,
                new ObjectMapper(),
                "warehouse-mcp/target/warehouse-mcp-0.1.0.jar",
                "http://localhost:8080",
                "logs/mcp");

        McpSession mcpSession = manager.bindSession(loginUser, session);

        assertThat(mcpSession.agentSessionId()).isEqualTo("session-1");
        assertThat(envRef.get()).containsEntry("WAREHOUSE_DELEGATED_TOKEN", "delegated.jwt.secret")
                .containsEntry("WAREHOUSE_AGENT_SESSION_ID", "session-1")
                .containsEntry("WAREHOUSE_API_BASE_URL", "http://localhost:8080");
        assertThat(envRef.get().get("WAREHOUSE_MCP_LOG_FILE")).contains("warehouse-mcp-session-1.log");
        assertThat(String.join(" ", commandRef.get())).doesNotContain("delegated.jwt.secret", "Authorization");
    }

    @Test
    void concurrentWarmupAndFirstToolBindingShareOneProcess() throws Exception {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        LoginUser loginUser = loginUser();
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(sessionService.issueDelegationTokenForInternalUse(loginUser, "session-1")).thenReturn("delegated.jwt.secret");
        when(sessionService.requireOwnedActiveSession(loginUser, "session-1")).thenReturn(session);

        AtomicInteger processStarts = new AtomicInteger();
        CountDownLatch factoryEntered = new CountDownLatch(1);
        CountDownLatch allowFactoryReturn = new CountDownLatch(1);
        McpProcessFactory factory = (command, environment) -> {
            processStarts.incrementAndGet();
            factoryEntered.countDown();
            try {
                if (!allowFactoryReturn.await(5, TimeUnit.SECONDS)) {
                    throw new java.io.IOException("Timed out waiting for concurrent binding test.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Concurrent binding test was interrupted.", e);
            }
            return new FakeProcess();
        };
        StdioMcpSessionManager manager = new StdioMcpSessionManager(
                sessionService,
                factory,
                new ObjectMapper(),
                "warehouse-mcp/target/warehouse-mcp-0.1.0.jar",
                "http://localhost:8080",
                "logs/mcp");

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var warmup = executor.submit(() -> manager.bindSession(loginUser, session));
            assertThat(factoryEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var firstToolBinding = executor.submit(() -> manager.bindSession(loginUser, session));
            allowFactoryReturn.countDown();

            assertThat(firstToolBinding.get(5, TimeUnit.SECONDS)).isSameAs(warmup.get(5, TimeUnit.SECONDS));
            assertThat(processStarts).hasValue(1);
        } finally {
            manager.closeAllSessions();
        }
    }

    @Test
    void closesStartedProcessWhenSessionIsRevokedDuringWarmup() {
        AgentSessionService sessionService = mock(AgentSessionService.class);
        LoginUser loginUser = loginUser();
        AgentSession session = new AgentSession();
        session.setId("session-1");
        session.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(sessionService.issueDelegationTokenForInternalUse(loginUser, "session-1")).thenReturn("delegated.jwt.secret");
        when(sessionService.requireOwnedActiveSession(loginUser, "session-1"))
                .thenThrow(new BusinessException(401, "Agent session is not active."));
        AtomicReference<FakeProcess> processRef = new AtomicReference<>();
        McpProcessFactory factory = (command, environment) -> {
            FakeProcess process = new FakeProcess();
            processRef.set(process);
            return process;
        };
        StdioMcpSessionManager manager = new StdioMcpSessionManager(
                sessionService,
                factory,
                new ObjectMapper(),
                "warehouse-mcp/target/warehouse-mcp-0.1.0.jar",
                "http://localhost:8080",
                "logs/mcp");

        assertThatThrownBy(() -> manager.bindSession(loginUser, session))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not active");
        assertThat(processRef.get().isAlive()).isFalse();
    }

    private LoginUser loginUser() {
        User user = new User();
        user.setId(7);
        user.setName("测试用户");
        user.setRoleCode("ADMIN");
        return new LoginUser(user, List.of(new SimpleGrantedAuthority("record:query")));
    }

    private static class FakeProcess extends Process {
        private static final String CAPABILITY_RESPONSES = """
                {"jsonrpc":"2.0","id":1,"result":{}}
                {"jsonrpc":"2.0","id":2,"result":{"tools":[
                {"name":"resolve_products"},{"name":"resolve_warehouses"},{"name":"get_inventory_overview"},
                {"name":"get_inventory_distribution"},{"name":"query_assay_records"},{"name":"get_assay_report_detail"},
                {"name":"query_assay_abnormalities"},{"name":"query_products_without_recent_assay"},
                {"name":"query_assay_standard_coverage"},{"name":"query_qr_code_lifecycle"},
                {"name":"query_printed_not_inbound_codes"},{"name":"query_pallet_anomalies"},
                {"name":"query_pallet_flow_records"},{"name":"query_qr_batch_inbound_completion"},
                {"name":"resolve_production_entities"},{"name":"query_production_order_progress"},
                {"name":"query_boiling_batch_trace"},
                {"name":"query_material_pick_trace"},
                {"name":"query_production_label_completion"},
                {"name":"query_in_process_materials"},
                {"name":"query_material_candidates"},
                {"name":"query_pallet_tasks"},
                {"name":"query_stock_documents"},
                {"name":"query_auto_inbound_batches"},
                {"name":"get_auto_inbound_batch_detail"},
                {"name":"query_warehouse_capacity_distribution"},
                {"name":"query_warehouse_recent_operations"},
                {"name":"query_warehouse_mixed_storage_facts"},
                {"name":"query_product_catalog"},
                {"name":"get_product_detail"},
                {"name":"query_screen_mesh_catalog"},
                {"name":"query_assay_groups"},
                {"name":"query_quality_standard_catalog"},
                {"name":"get_quality_standard_detail"},
                {"name":"query_product_standard_relations"},
                {"name":"query_employee_roster"},
                {"name":"query_roles"},
                {"name":"get_role_permission_summary"},
                {"name":"search_operation_logs"},
                {"name":"query_agent_tool_audit"},
                {"name":"query_agent_answer_reviews"},
                {"name":"query_inventory_ledger"},
                {"name":"query_prepare_pool_balance"},
                {"name":"query_fixed_product_qr_pool"},
                {"name":"get_warehouse_status"},{"name":"get_pallet_status"},{"name":"get_assay_status"}]}}
                """;
        private boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            List<String> lines = CAPABILITY_RESPONSES.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
            String wire = lines.get(0) + "\n" + String.join("", lines.subList(1, lines.size())) + "\n";
            return new ByteArrayInputStream(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            alive = false;
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
