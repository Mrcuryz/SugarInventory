package com.Laibin.SugarInventory.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PrintedNotInboundCodesQueryDTO {
    @Valid
    private AssayRecordsQueryDTO.ProductScope productScope;

    @Size(max = 50)
    private String orderNo;

    @Size(max = 80)
    private String batchNo;

    @Valid
    private AssayRecordsQueryDTO.DateRange dateRange;

    @Size(max = 30)
    private String groupBy;

    @Min(1)
    @Max(100)
    private Integer limit;

    @JsonIgnore
    private LocalDate resolvedFrom;

    @JsonIgnore
    private LocalDate resolvedTo;
}
