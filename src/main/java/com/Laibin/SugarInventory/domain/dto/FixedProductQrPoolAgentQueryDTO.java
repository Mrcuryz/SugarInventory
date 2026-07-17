package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import java.util.List;

@Data
public class FixedProductQrPoolAgentQueryDTO {
    private String productName;
    private List<String> codes;
    private String status;
    private Boolean freeOnly;
    private Integer page = 1;
    private Integer size = 20;
}
