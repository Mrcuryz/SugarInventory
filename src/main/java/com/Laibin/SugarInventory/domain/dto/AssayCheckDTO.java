package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Data
@Schema(description = "化验记录存在性查询DTO")
public class AssayCheckDTO {
    private Integer productId;
    private LocalDate entryDate;
}
