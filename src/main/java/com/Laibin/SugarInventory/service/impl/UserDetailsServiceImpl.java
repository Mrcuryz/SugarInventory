package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.service.PermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.Laibin.SugarInventory.domain.po.Permission;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    public UserDetails loadUserByUsername(String idOrEmployeeId) {
        User user = userMapper.selectByEmployeeId(idOrEmployeeId);  // 先按工号查

        if (user == null && idOrEmployeeId.matches("\\d+")) {
            user = userMapper.selectById(Integer.parseInt(idOrEmployeeId));  // 如果按工号查不到，再按ID查
        }

        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 查询权限列表
        List<Permission> permissions = permissionMapper.selectPermissionsByRoleCode(user.getRoleCode());
        // 构建权限集合
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(p -> new SimpleGrantedAuthority(p.getPermCode()))
                .collect(Collectors.toList());

        return new LoginUser(user, authorities);
    }
}