package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建成品出库任务请求")
public class CreateFinishOutTaskDTO {
    @NotEmpty
    @Valid
    @Schema(description = "成品托盘码列表")
    private List<String> codes;

    @Schema(description = "备注，可选")
    private String remark;
}
