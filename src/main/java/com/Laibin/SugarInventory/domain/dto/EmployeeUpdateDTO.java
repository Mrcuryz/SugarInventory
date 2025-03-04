package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class EmployeeUpdateDTO extends BaseDTO {
    @NotNull(message = "员工ID不能为空")
    private Integer id;  // 员工唯一标识

    private String employeeId; // 工号
    private String name;       // 姓名
    private String mobile;     // 手机号
    private String department; // 所属部门
    private String position;   // 职位
    private String status;     // 在职/离职
    private String roleCode;   // 角色代码 (ADMIN/QC/STAFF)

    @Override
    public Integer getId() {
        return id;
    }
}
