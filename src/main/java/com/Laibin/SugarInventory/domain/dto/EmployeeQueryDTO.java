package com.Laibin.SugarInventory.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "员工查询DTO")
public class EmployeeQueryDTO {
    @Schema(description = "工号")
    private String employeeId;  // 工号（支持模糊查询）
    @Schema(description = "姓名")
    private String name;        // 姓名（支持模糊查询）
    @Schema(description = "手机号")
    private String mobile;      // 手机号（支持模糊查询）
    @Schema(description = "所属部门")
    private String department;  // 所属部门（支持模糊查询）
    @Schema(description = "职位")
    private String position;    // 职位（支持模糊查询）
    @Schema(description = "状态")
    private String status;      // 状态（例如：在职、离职）
    @Schema(description = "预设角色")
    private String roleCode;    // 预设角色
    // 分页参数，默认第一页，每页10条
    @Schema(description = "页码")
    private Integer page = 1;
    @Schema(description = "每页条数")
    private Integer size = 10;
}
