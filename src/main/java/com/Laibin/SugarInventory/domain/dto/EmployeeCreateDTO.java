package com.Laibin.SugarInventory.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmployeeCreateDTO {
    @NotBlank(message = "工号不能为空")
    private String employeeId;

    @NotBlank(message = "姓名不能为空")
    private String name;

    private String mobile;
    private String department;
    private String position;

    @NotBlank(message = "员工状态不能为空")
    private String status;

    @NotBlank(message = "角色不能为空")
    private String roleCode;
}
