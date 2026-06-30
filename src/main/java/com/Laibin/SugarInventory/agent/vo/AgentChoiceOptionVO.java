package com.Laibin.SugarInventory.agent.vo;

import lombok.Data;

@Data
public class AgentChoiceOptionVO {
    private String optionId;
    private String optionType;
    private String displayLabel;
    private String description;
    private Integer productId;
    private Integer warehouseId;
    private Boolean supported;
    private String disabledReason;
}