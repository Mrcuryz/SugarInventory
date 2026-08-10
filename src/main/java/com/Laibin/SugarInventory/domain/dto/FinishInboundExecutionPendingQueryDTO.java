package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class FinishInboundExecutionPendingQueryDTO {
    @NotEmpty
    @Size(max = 20)
    private List<String> palletCodes;
}
