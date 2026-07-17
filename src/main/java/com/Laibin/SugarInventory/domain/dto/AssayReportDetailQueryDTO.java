package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssayReportDetailQueryDTO {
    @NotBlank
    @Size(min = 1, max = 200)
    private String reportRef;

    private Boolean includeMetrics;

    private Boolean includeStandardSnapshot;
}
