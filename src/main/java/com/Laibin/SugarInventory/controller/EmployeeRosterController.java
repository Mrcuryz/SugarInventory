package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.assembler.ManagementViewAssembler;
import com.Laibin.SugarInventory.domain.dto.EmployeeCreateDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.vo.EmployeeRosterVO;
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
    @PreAuthorize("hasAnyAuthority('employee:create','user:create')")
    @LogOperation(value = "员工名册", type = OperationType.INSERT)
    @PostMapping("/import")
    public Result<String> importEmployeeRoster(
            @Parameter(description = "员工名册EXCEL文件")
            @RequestParam("file") MultipartFile file) {
        employeeService.importEmployeeRoster(file);
        return Result.success("员工名册导入成功");
    }

    @Operation(summary = "新增员工")
    @PreAuthorize("hasAnyAuthority('employee:create','user:create')")
    @LogOperation(value = "员工名册", type = OperationType.INSERT)
    @PostMapping("/add")
    public Result<String> addEmployee(@Validated @RequestBody EmployeeCreateDTO dto) {
        employeeService.save(ManagementViewAssembler.toEmployeeRoster(dto));
        return Result.success("员工信息新增成功");
    }

    @Operation(summary = "根据条件（可选）查询员工名册")
    @PreAuthorize("hasAuthority('rbac:user:view')")
    @PostMapping("/query")
    public Result<PageResult<EmployeeRosterVO>> queryEmployee(@RequestBody EmployeeQueryDTO queryDTO) {
        return Result.success(ManagementViewAssembler.toEmployeeRosterPage(employeeService.queryEmployee(queryDTO)));
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工信息", description = "根据ID修改员工信息，可选更新姓名、手机号、部门、职位、状态、角色等")
    @PreAuthorize("hasAnyAuthority('employee:update','user:update')")
    @LogOperation(value = "员工名册", type = OperationType.UPDATE)
    public Result<EmployeeRosterVO> updateEmployee(@Validated @RequestBody EmployeeUpdateDTO dto) {
        return Result.success(ManagementViewAssembler.toEmployeeRosterVO(employeeService.updateEmployee(dto)));
    }

    @DeleteMapping("/clearResigned")
    @Operation(summary = "清理离职员工", description = "删除所有状态为“离职”的员工记录")
    @PreAuthorize("hasAnyAuthority('employee:delete','user:delete')")
    @LogOperation(value = "员工名册", type = OperationType.DELETE)
    public Result<String> clearResignedEmployees() {
        int deletedRows = employeeService.clearResignedEmployees();
        return Result.success("成功清理 " + deletedRows + " 条离职员工记录");
    }
}
