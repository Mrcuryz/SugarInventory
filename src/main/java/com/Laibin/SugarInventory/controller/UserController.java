package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.po.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户相关接口")
public class UserController {

    @GetMapping("/info")
    @Operation(summary = "获取用户信息", description = "根据JWT Token解析用户信息，返回用户姓名、工号、角色代码")
    public Result<Map<String, Object>> getUserInfo(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null) {
            return Result.error(401, "未授权访问");
        }

        User user = loginUser.getUser();
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("name", user.getName());
        userInfo.put("employeeId", user.getEmployeeId());
        userInfo.put("roleCode", user.getRoleCode());

        return Result.success(userInfo);
    }
}
