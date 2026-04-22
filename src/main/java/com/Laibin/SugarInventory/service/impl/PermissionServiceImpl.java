package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.domain.po.Permission;
import com.Laibin.SugarInventory.domain.vo.PermissionVO;
import com.Laibin.SugarInventory.mapper.PermissionMapper;
import com.Laibin.SugarInventory.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {
    @Override
    public List<PermissionVO> listPermissionVOs() {
        return this.list(new LambdaQueryWrapper<Permission>()
                        .orderByAsc(Permission::getPermCode))
                .stream()
                .map(this::toPermissionVO)
                .collect(Collectors.toList());
    }

    private PermissionVO toPermissionVO(Permission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setPermCode(permission.getPermCode());
        vo.setPermName(permission.getPermName());
        vo.setDescription(permission.getDescription());
        return vo;
    }
}
