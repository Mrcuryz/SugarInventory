package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class WarehouseRecentOperationsAgentQueryDTO {
    private Integer warehouseId;
    private LocalDateTime from;
    private LocalDateTime to;
    private List<String> eventTypes = new ArrayList<>();
    private Integer limit = 20;
}
