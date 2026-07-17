package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class AutoInboundBatchesAgentQueryDTO {
    private String status;
    private Integer limit = 20;
}
