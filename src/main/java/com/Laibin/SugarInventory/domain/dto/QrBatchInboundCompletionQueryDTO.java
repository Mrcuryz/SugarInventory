package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class QrBatchInboundCompletionQueryDTO {
    @Size(max = 80)
    private String batchNo;

    @Size(max = 50)
    private String orderNo;

    @Min(1)
    private Integer productId;

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    private Boolean includeUnfinishedExamples;

    @Min(1)
    @Max(100)
    private Integer limit;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;
}
