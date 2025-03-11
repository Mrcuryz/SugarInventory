package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

// 工号验证DTO
@Data
@Schema(description = "工号验证绑定请求DTO")
public class EmployeeVerifyDTO {
    @Schema(description = "工号", example = "EMP001")
    private String employeeId;

    @Schema(description = "部分姓名验证，例如 '张*三'", example = "张*三")
    private String namePart;

    @Schema(description = "微信登录凭证", example = "0c3uP8ll2TZU8f4ganml266lf83uP8l2")
    private String code;
}