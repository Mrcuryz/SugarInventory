package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量出库查询DTO")
public class OutStockBatchQueryDTO {
    private List<Integer> ids;
    private Integer page;
    private Integer size;
}
