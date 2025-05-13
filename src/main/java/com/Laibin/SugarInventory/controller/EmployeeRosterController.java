package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "员工名册管理")
@RequestMapping("/api/employee")
public class EmployeeRosterController {
    @Autowired
    private EmployeeService employeeService;
    @Operation(summary = "导入员工名册")
    @PreAuthorize("hasAuthority('user:create')")
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

    @Operation(summary = "新增员工")
    @PreAuthorize("hasAuthority('user:create')")
    @PostMapping("/add")
    public Result<String> addEmployee(@Validated @RequestBody EmployeeRoster employeeRoster) {
        try {
            employeeService.save(employeeRoster);
            return Result.success("员工信息新增成功");
        } catch (Exception e) {
            return Result.error(500, "员工信息新增失败: " + e.getMessage());
        }
    }

    @Operation(summary = "根据条件（可选）查询员工名册")
    @PostMapping("/query")
    public Result<PageResult<EmployeeRoster>> queryEmployee(@RequestBody EmployeeQueryDTO queryDTO) {
        PageResult<EmployeeRoster> result = employeeService.queryEmployee(queryDTO);
        return Result.success(result);
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工信息", description = "根据ID修改员工信息，可选更新姓名、手机号、部门、职位、状态、角色等")
    @PreAuthorize("hasAuthority('user:update')")
    @LogOperation(value = "员工名册", type = OperationType.UPDATE)
    public Result<EmployeeRoster> updateEmployee(@Validated @RequestBody EmployeeUpdateDTO dto) {
        try {
            return Result.success(employeeService.updateEmployee(dto));
        } catch (Exception e) {
            return Result.error(500, "员工信息更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/clearResigned")
    @Operation(summary = "清理离职员工", description = "删除所有状态为“离职”的员工记录")
    @PreAuthorize("hasAuthority('user:delete')")
    @LogOperation(value = "员工名册", type = OperationType.DELETE)
    public Result<String> clearResignedEmployees() {
        try {
            int deletedRows = employeeService.clearResignedEmployees();
            return Result.success("成功清理 " + deletedRows + " 条离职员工记录");
        } catch (Exception e) {
            return Result.error(500, "清理失败：" + e.getMessage());
        }
    }
}
