package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PreparePoolBalanceAgentQueryDTO {
    private String productName;
    private String productType;
    private String screenMeshName;
    private LocalDate productionDateStart;
    private LocalDate productionDateEnd;
    private Boolean positiveOnly = true;
    private Integer page = 1;
    private Integer size = 20;
}
