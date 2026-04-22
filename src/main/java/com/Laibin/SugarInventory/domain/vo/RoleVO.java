package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "角色 VO")
public class RoleVO {
    @Schema(description = "角色ID")
    private Integer id;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色编码")
    private String roleCode;

    @Schema(description = "角色说明")
    private String description;

    @Schema(description = "角色状态")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "权限数量")
    private Integer permissionCount;

    @Schema(description = "用户数量")
    private Integer userCount;

    @Schema(description = "使用该角色的用户名称列表")
    private List<String> userNames = new ArrayList<>();

    @Schema(description = "当前角色勾选的权限ID列表")
    private List<Integer> permissionIds = new ArrayList<>();
}
