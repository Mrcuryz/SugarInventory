package com.Laibin.SugarInventory.domain.dto;

import lombok.Data;

@Data
public class EmployeeRosterAgentQueryDTO {
    private String employeeId;
    private String name;
    private String department;
    private String position;
    private String status;
    private String roleCode;
    private Integer page = 1;
    private Integer size = 20;
}
