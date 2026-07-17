package com.Laibin.SugarInventory.agent.mcp;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.agent.service.AgentSessionService;
import com.Laibin.SugarInventory.domain.po.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StdioMcpSessionManager implements McpSessionManager {
    private static final Logger log = LoggerFactory.getLogger(StdioMcpSessionManager.class);
    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "resolve_products", "resolve_warehouses", "get_inventory_overview", "get_inventory_distribution",
            "query_assay_records", "get_assay_report_detail", "query_assay_abnormalities",
            "query_products_without_recent_assay", "query_assay_standard_coverage", "query_qr_code_lifecycle",
            "query_printed_not_inbound_codes", "query_pallet_anomalies", "query_pallet_flow_records",
            "query_qr_batch_inbound_completion", "resolve_production_entities", "query_production_order_progress",
            "query_boiling_batch_trace",
            "query_material_pick_trace",
            "query_production_label_completion",
            "query_in_process_materials",
            "query_material_candidates",
            "query_pallet_tasks",
            "query_stock_documents",
            "query_auto_inbound_batches",
            "get_auto_inbound_batch_detail",
            "query_warehouse_capacity_distribution",
            "query_warehouse_recent_operations",
            "query_warehouse_mixed_storage_facts",
            "query_product_catalog",
            "get_product_detail",
            "query_screen_mesh_catalog",
            "query_assay_groups",
            "query_quality_standard_catalog",
            "get_quality_standard_detail",
            "query_product_standard_relations",
            "query_employee_roster",
            "query_roles",
            "get_role_permission_summary",
            "search_operation_logs",
            "query_agent_tool_audit",
            "query_agent_answer_reviews",
            "query_inventory_ledger",
            "query_prepare_pool_balance",
            "query_fixed_product_qr_pool",
            "get_warehouse_status", "get_pallet_status", "get_assay_status");

    private final AgentSessionService agentSessionService;
    private final McpProcessFactory processFactory;
    private final ObjectMapper objectMapper;
    private final String jarPath;
    private final String apiBaseUrl;
    private final String logFileDirectory;
    private final Map<String, ManagedSession> sessions = new ConcurrentHashMap<>();

    public StdioMcpSessionManager(AgentSessionService agentSessionService,
                                  McpProcessFactory processFactory,
                                  ObjectMapper objectMapper,
                                  @Value("${agent.mcp.jar-path:warehouse-mcp/target/warehouse-mcp-0.1.0-exec.jar}") String jarPath,
                                  @Value("${agent.mcp.api-base-url:http://localhost:8080}") String apiBaseUrl,
                                  @Value("${agent.mcp.log-dir:logs/mcp}") String logFileDirectory) {
        this.agentSessionService = agentSessionService;
        this.processFactory = processFactory;
        this.objectMapper = objectMapper;
        this.jarPath = jarPath;
        this.apiBaseUrl = apiBaseUrl;
        this.logFileDirectory = logFileDirectory;
    }

    @Override
    public McpSession bindSession(LoginUser loginUser, AgentSession agentSession) {
        cleanupExpiredSessions();
        ManagedSession managed = sessions.compute(agentSession.getId(), (agentSessionId, existing) -> {
            if (existing != null && existing.process().isAlive()) {
                return existing;
            }
            if (existing != null) {
                existing.session().close();
            }
            return startSession(loginUser, agentSession);
        });
        return managed.session();
    }

    private ManagedSession startSession(LoginUser loginUser, AgentSession agentSession) {
        String delegatedToken = agentSessionService.issueDelegationTokenForInternalUse(loginUser, agentSession.getId());
        Map<String, String> environment = Map.of(
                "WAREHOUSE_DELEGATED_TOKEN", delegatedToken,
                "WAREHOUSE_AGENT_SESSION_ID", agentSession.getId(),
                "WAREHOUSE_API_BASE_URL", apiBaseUrl,
                "WAREHOUSE_MCP_LOG_FILE", Path.of(logFileDirectory, "warehouse-mcp-" + agentSession.getId() + ".log").toString()
        );
        StdioMcpSession session = null;
        try {
            Process process = processFactory.start(List.of("java", "-jar", jarPath), environment);
            session = new StdioMcpSession(agentSession.getId(), process, objectMapper);
            Set<String> actualTools = session.listTools();
            if (!EXPECTED_TOOLS.equals(actualTools)) {
                throw new IllegalStateException("Warehouse MCP capability registry mismatch.");
            }
            AgentSession activeSession = agentSessionService.requireOwnedActiveSession(loginUser, agentSession.getId());
            return new ManagedSession(session, process, activeSession.getExpiresAt());
        } catch (IOException e) {
            closeQuietly(session);
            log.warn("Failed to start MCP process for agent session {}: {}", agentSession.getId(), e.getMessage());
            throw new IllegalStateException("Unable to start MCP session.");
        } catch (RuntimeException e) {
            closeQuietly(session);
            throw e;
        }
    }

    @Override
    public void closeSession(String agentSessionId) {
        ManagedSession managed = sessions.remove(agentSessionId);
        if (managed != null) {
            managed.session().close();
        }
    }

    @Override
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessions.forEach((agentSessionId, managed) -> {
            boolean expired = managed.expiresAt() != null && managed.expiresAt().isBefore(now);
            boolean dead = !managed.process().isAlive();
            if ((expired || dead) && sessions.remove(agentSessionId, managed)) {
                managed.session().close();
            }
        });
    }

    @PreDestroy
    public void closeAllSessions() {
        sessions.forEach((agentSessionId, managed) -> {
            if (sessions.remove(agentSessionId, managed)) {
                managed.session().close();
            }
        });
    }

    private void closeQuietly(McpSession session) {
        if (session != null) {
            session.close();
        }
    }

    private record ManagedSession(McpSession session, Process process, LocalDateTime expiresAt) {
    }
}
