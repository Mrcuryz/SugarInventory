package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量删除托盘流转记录请求")
public class DeletePalletFlowBatchDTO {
    @NotEmpty
    @Schema(description = "流转记录ID列表")
    private List<Long> ids;
}
