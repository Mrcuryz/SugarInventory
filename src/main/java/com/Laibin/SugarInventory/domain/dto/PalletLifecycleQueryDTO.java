package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PalletLifecycleQueryDTO {
    @NotBlank
    @Size(max = 100)
    private String code;

    private Boolean includeInventory;
    private Boolean includeAssay;
    private Boolean includeFlows;
    private Boolean includePrintInfo;

    @Min(1)
    @Max(100)
    private Integer flowLimit;
}
