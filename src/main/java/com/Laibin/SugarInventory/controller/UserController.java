package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.Role;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.UserInfoVO;
import com.Laibin.SugarInventory.mapper.EmployeeRosterMapper;
import com.Laibin.SugarInventory.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户管理", description = "用户相关接口")
public class UserController {
    @Autowired
    private EmployeeRosterMapper employeeRosterMapper;

    @Autowired
    private RoleMapper roleMapper;

    @GetMapping("/info")
    @Operation(summary = "获取用户信息", description = "根据 JWT Token 解析用户信息，返回姓名、工号、角色、手机号与权限。")
    public Result<UserInfoVO> getUserInfo(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null) {
            return Result.error(401, "未授权访问");
        }

        User user = loginUser.getUser();
        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setName(user.getName());
        userInfo.setEmployeeId(user.getEmployeeId());
        userInfo.setRoleCode(user.getRoleCode());
        userInfo.setBindMethod(user.getBindMethod() == null ? null : user.getBindMethod().name());

        EmployeeRoster roster = employeeRosterMapper.selectByEmployeeId(user.getEmployeeId());
        if (roster != null) {
            userInfo.setMobile(roster.getMobile());
            userInfo.setPhone(roster.getMobile());
        }

        if (user.getRoleCode() != null && !user.getRoleCode().isBlank()) {
            Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                    .eq(Role::getRoleCode, user.getRoleCode())
                    .last("LIMIT 1"));
            if (role != null) {
                userInfo.setRoleName(role.getRoleName());
            }
        }

        userInfo.setPermissionCodes(loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .distinct()
                .collect(Collectors.toList()));

        return Result.success(userInfo);
    }
}
