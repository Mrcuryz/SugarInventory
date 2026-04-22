package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "角色状态更新 DTO")
public class RoleStatusUpdateDTO {
    @NotBlank(message = "角色状态不能为空")
    @Schema(description = "角色状态", example = "ENABLED")
    private String status;
}
