package com.Laibin.SugarInventory.agent;

import com.Laibin.SugarInventory.agent.mcp.McpRuntimeArtifactVerifier;
import com.Laibin.SugarInventory.agent.mcp.StdioMcpSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class McpRuntimeArtifactVerifierTest {
    @Test
    void verifiesRuntimeArtifactWhenEnabled() {
        StdioMcpSessionManager sessionManager = mock(StdioMcpSessionManager.class);

        new McpRuntimeArtifactVerifier(sessionManager, true)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(sessionManager).verifyRuntimeCapabilities();
    }

    @Test
    void skipsRuntimeArtifactVerificationOnlyWhenExplicitlyDisabled() {
        StdioMcpSessionManager sessionManager = mock(StdioMcpSessionManager.class);

        new McpRuntimeArtifactVerifier(sessionManager, false)
                .run(new DefaultApplicationArguments(new String[0]));

        verify(sessionManager, never()).verifyRuntimeCapabilities();
    }
}
