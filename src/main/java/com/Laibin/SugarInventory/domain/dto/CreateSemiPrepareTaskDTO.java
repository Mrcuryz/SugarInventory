package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "创建半成品转入备料池任务请求")
public class CreateSemiPrepareTaskDTO {
    @NotEmpty
    @Valid
    @Schema(description = "半成品托盘码列表")
    private List<String> codes;

    @Schema(description = "备注，建议填写备料池实际位置")
    private String remark;
}
