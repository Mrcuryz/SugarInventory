package com.Laibin.SugarInventory.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "当前登录用户信息 VO")
public class UserInfoVO {
    @Schema(description = "姓名")
    private String name;

    @Schema(description = "工号")
    private String employeeId;

    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "角色代码")
    private String roleCode;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "手机号（兼容旧字段）")
    private String phone;

    @Schema(description = "绑定方式")
    private String bindMethod;

    @Schema(description = "权限编码集合")
    private List<String> permissionCodes;
}
