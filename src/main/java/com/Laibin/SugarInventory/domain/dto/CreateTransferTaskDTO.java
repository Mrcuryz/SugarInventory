package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建托盘调拨任务请求")
public class CreateTransferTaskDTO {
    @NotEmpty
    @Valid
    @Schema(description = "调拨任务明细列表")
    private List<CreateTransferTaskItemDTO> items;
}
