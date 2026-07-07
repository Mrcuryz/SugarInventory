package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentSessionVO {
    private String agentSessionId;
    private Integer userId;
    private String name;
    private String roleCode;
    private List<String> permissionCodes;
    private List<String> scopes;
    private String status;
    private LocalDateTime expiresAt;
    private String modelDisplayName;
    private String mcpServerName;
    private String mcpTransport;
}
