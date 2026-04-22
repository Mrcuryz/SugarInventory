package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "角色权限分配 DTO")
public class RolePermissionUpdateDTO {
    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID")
    private Integer roleId;

    @Schema(description = "权限ID列表")
    private List<Integer> permissionIds;
}
