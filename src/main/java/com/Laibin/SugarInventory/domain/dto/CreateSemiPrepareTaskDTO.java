package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建历史生产占用任务请求")
public class CreateSemiPrepareTaskDTO {
    @NotEmpty
    @Valid
    @Schema(description = "半成品托盘码列表")
    private List<String> codes;

    @Schema(description = "备注，可填写历史占用说明")
    private String remark;
}
