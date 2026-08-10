package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FinishInboundExecutionConfirmationDTO {
    @NotBlank(message = "成品入库执行预览引用不能为空")
    @Pattern(regexp = "^fip1_[A-Za-z0-9_-]{43}$", message = "成品入库执行预览引用无效")
    private String previewRef;
}
