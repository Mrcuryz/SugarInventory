package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "角色新增/更新 DTO")
public class RoleSaveDTO extends BaseDTO {
    @Schema(description = "角色ID")
    private Integer id;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称")
    private String roleName;

    @NotBlank(message = "角色编码不能为空")
    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "角色状态", example = "ENABLED")
    private String status;

    @Override
    public Integer getId() {
        return id;
    }
}
