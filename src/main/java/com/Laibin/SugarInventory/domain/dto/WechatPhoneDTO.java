package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 微信手机号解密DTO
@Data
@Schema(description = "微信手机号绑定请求DTO")
public class WechatPhoneDTO {
    @Schema(description = "微信登录凭证", example = "0c3uP8ll2TZU8f4ganml266lf83uP8l2")
    private String code;

    @Schema(description = "加密的微信手机号", example = "暂无")
    private String phoneCode;
}