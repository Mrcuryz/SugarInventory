package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FinishInboundExecutionTokenDTO {
    @NotBlank(message = "执行令牌不能为空")
    @Pattern(regexp = "^fiet1_[A-Za-z0-9_-]{43}$", message = "执行令牌无效")
    private String executionToken;

    @NotBlank(message = "幂等键不能为空")
    @Pattern(regexp = "^fii1_[A-Za-z0-9_-]{43}$", message = "幂等键无效")
    private String idempotencyKey;
}
