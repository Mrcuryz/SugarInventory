package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshCreateDTO;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.service.ScreenMeshService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/screen-mesh")
@Tag(name = "筛网管理", description = "筛网的增删改查接口，用于管理不同规格的筛网")
public class ScreenMeshController {

    @Autowired
    private ScreenMeshService screenMeshService;

    @Operation(summary = "查询筛网列表", description = "根据筛网名称模糊查询筛网列表，如果不传参数则返回全部匹配记录")
    @GetMapping("/list")
    public Result<List<ScreenMesh>> getScreenMeshes(
            @Parameter(description = "筛网名称，支持模糊查询", example = "大筛网", required = false)
            @RequestParam(required = false) String meshName) {
        System.out.println("meshName: " + meshName);
        try {
            return Result.success(screenMeshService.findScreenMeshes(meshName));
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "查询所有筛网", description = "返回所有筛网信息")
    @GetMapping("/all")
    public Result<List<ScreenMesh>> getAllScreenMeshes() {
        return Result.success(screenMeshService.findAllScreenMesh());
    }

    @Operation(summary = "添加筛网", description = "新增一条筛网记录，包含筛网名称、描述、创建人等信息")
    @LogOperation(value = "筛网", type = OperationType.INSERT)
    @PostMapping("/add")
    public Result<ScreenMesh> addScreenMesh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "筛网对象，包含筛网名称、描述等必填字段", required = true)
            @Valid @RequestBody ScreenMeshCreateDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        try {
            return Result.success(screenMeshService.addScreenMesh(dto, loginUser.getUser().getId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "更新筛网", description = "更新一条筛网记录，需传入筛网ID以及更新后的数据")
    @LogOperation(value = "筛网", type = OperationType.UPDATE)
    @PutMapping("/update")
    public Result<ScreenMesh> updateScreenMesh(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "筛网对象，必须包含ID及需要更新的字段", required = true)
            @Valid @RequestBody ScreenMeshUpdateDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        try {
            return Result.success(screenMeshService.updateScreenMesh(dto, loginUser.getUser().getId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Operation(summary = "删除筛网", description = "根据筛网ID删除筛网记录")
    @LogOperation(value = "筛网", type = OperationType.DELETE)
    @DeleteMapping("/delete/{id}")
    public Result<String> deleteScreenMesh(
            @Parameter(description = "筛网ID", example = "1", required = true)
            @PathVariable Integer id,
            @AuthenticationPrincipal LoginUser loginUser) {
        try {
            screenMeshService.deleteScreenMesh(id);
            return Result.success("成功删除筛网");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
