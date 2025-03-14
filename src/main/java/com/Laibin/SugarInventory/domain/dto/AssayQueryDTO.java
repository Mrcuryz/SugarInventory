package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "化验记录查询条件DTO")
public class AssayQueryDTO extends BaseDTO {
    @Schema(description = "产品名称，支持模糊查询", example = "中冰")
    private String productName;

    @Schema(description = "查询起始日期", example = "2025-01-01")
    private LocalDate startDate;

    @Schema(description = "查询结束日期", example = "2025-03-31")
    private LocalDate endDate;

    @Schema(description = "化验人员姓名，支持模糊查询", example = "1")
    private String testerName;

    @Schema(description = "当前页码", example = "1")
    private Integer page = 1;

    @Schema(description = "每页记录数", example = "10")
    private Integer size = 10;

    @Override
    public Integer getId() {
        return null;
    }
}