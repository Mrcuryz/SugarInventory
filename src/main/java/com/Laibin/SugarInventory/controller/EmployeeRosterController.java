package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "员工名册管理")
@RequestMapping("/api/employee")
public class EmployeeRosterController {
    @Autowired
    private EmployeeService employeeService;
    @Operation(summary = "导入员工名册")
    @PostMapping("/import")
    public Result<String> importEmployeeRoster(
            @Parameter(description = "员工名册EXCEL文件")
            @RequestParam("file") MultipartFile file) {
        try {
            employeeService.importEmployeeRoster(file);
            return Result.success("员工名册导入成功");
        } catch (Exception e) {
            return Result.error("员工名册导入失败：" + e.getMessage());
        }
    }

    @Operation(summary = "根据条件（可选）查询员工名册")
    @PostMapping("/query")
    public Result<PageResult<EmployeeRoster>> queryEmployee(@RequestBody EmployeeQueryDTO queryDTO) {
        PageResult<EmployeeRoster> result = employeeService.queryEmployee(queryDTO);
        return Result.success(result);
    }
}
