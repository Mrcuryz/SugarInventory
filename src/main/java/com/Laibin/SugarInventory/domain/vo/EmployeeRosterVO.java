package com.Laibin.SugarInventory.domain.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class EmployeeRosterVO extends BaseVO {
    private Integer id;
    private String employeeId;
    private String name;
    private String mobile;
    private String department;
    private String position;
    private String status;
    private String roleCode;
    private LocalDateTime createdAt;
}
