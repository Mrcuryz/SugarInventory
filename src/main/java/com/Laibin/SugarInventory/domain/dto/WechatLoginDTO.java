package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "微信登录请求DTO")
public class WechatLoginDTO {
    @NotBlank(message = "微信临时登录凭证不能为空")
    @Schema(description = "微信临时登录凭证", example = "0c3uP8ll2TZU8f4ganml266lf83uP8l2")
    private String code; // 微信临时登录凭证
}