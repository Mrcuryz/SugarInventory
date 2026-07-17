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
    private long pythonTimeoutMs = 30000;
    private String pythonServiceKey = "";
    private boolean fallbackEnabled = false;
}
