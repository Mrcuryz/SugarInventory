package com.Laibin.SugarInventory.agent.runtime;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.runtime")
public class AgentRuntimeProperties {
    public enum Mode {
        LEGACY,
        PYTHON
    }

    private Mode mode = Mode.PYTHON;
    private String pythonBaseUrl = "http://localhost:8091";
    /**
     * Must stay above the Python LLM run budget (90 seconds by default) so that
     * Java can receive Python's controlled terminal event instead of cutting a
     * valid Agent turn off early.
     */
    private long pythonTimeoutMs = 100000;
    private String pythonServiceKey = "";
    private boolean fallbackEnabled = false;
}
