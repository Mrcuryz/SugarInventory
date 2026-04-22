package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.RolePermissionUpdateDTO;
import com.Laibin.SugarInventory.domain.dto.RoleQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleSaveDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.Laibin.SugarInventory.domain.po.Role;
import com.Laibin.SugarInventory.domain.po.RolePermission;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.RoleOptionVO;
import com.Laibin.SugarInventory.domain.vo.RoleVO;
import com.Laibin.SugarInventory.mapper.EmployeeRosterMapper;
import com.Laibin.SugarInventory.mapper.RoleMapper;
import com.Laibin.SugarInventory.mapper.RolePermissionMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.service.RoleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {
    private static final String ROLE_STATUS_ENABLED = "ENABLED";
    private static final String ROLE_STATUS_DISABLED = "DISABLED";
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private EmployeeRosterMapper employeeRosterMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<RoleVO> queryRoles(RoleQueryDTO queryDTO) {
        int page = queryDTO.getPage() == null || queryDTO.getPage() < 1 ? 1 : queryDTO.getPage();
        int size = queryDTO.getSize() == null || queryDTO.getSize() < 1 ? 10 : queryDTO.getSize();
        int offset = (page - 1) * size;

        LambdaQueryWrapper<Role> wrapper = buildQueryWrapper(queryDTO);
        Long total = this.baseMapper.selectCount(wrapper);
        List<Role> roles = this.baseMapper.selectList(wrapper.last("LIMIT " + offset + "," + size));
        return new PageResult<>(total, buildRoleVOList(roles, false));
    }

    @Override
    public List<RoleOptionVO> listEnabledRoleOptions() {
        return this.list(new LambdaQueryWrapper<Role>()
                        .eq(Role::getStatus, ROLE_STATUS_ENABLED)
                        .orderByAsc(Role::getCreatedAt, Role::getId))
                .stream()
                .map(this::toRoleOptionVO)
                .collect(Collectors.toList());
    }

    @Override
    public RoleVO getRoleDetail(Integer id) {
        Role role = requireRole(id);
        return buildRoleVOList(Collections.singletonList(role), true).get(0);
    }

    @Override
    @Transactional
    public RoleVO createRole(RoleSaveDTO dto) {
        String roleCode = normalizeRoleCode(dto.getRoleCode());
        ensureRoleCodeUnique(null, roleCode);

        Role role = new Role();
        role.setRoleName(normalizeText(dto.getRoleName(), "角色名称不能为空"));
        role.setRoleCode(roleCode);
        role.setDescription(trimToNull(dto.getDescription()));
        role.setStatus(normalizeStatus(dto.getStatus()));
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        this.save(role);
        return getRoleDetail(role.getId());
    }

    @Override
    @Transactional
    public RoleVO updateRole(Integer id, RoleSaveDTO dto) {
        Role role = requireRole(id);
        String nextRoleCode = normalizeRoleCode(dto.getRoleCode());
        ensureAdminRoleImmutable(role, nextRoleCode);
        ensureRoleCodeUnique(id, nextRoleCode);

        role.setRoleName(normalizeText(dto.getRoleName(), "角色名称不能为空"));
        role.setRoleCode(nextRoleCode);
        role.setDescription(trimToNull(dto.getDescription()));
        role.setStatus(normalizeStatus(dto.getStatus()));
        role.setUpdatedAt(LocalDateTime.now());
        this.updateById(role);
        return getRoleDetail(id);
    }

    @Override
    @Transactional
    public void updateRolePermissions(Integer id, RolePermissionUpdateDTO dto) {
        Role role = requireRole(id);
        if (!Objects.equals(id, dto.getRoleId())) {
            throw new BusinessException("角色ID不一致");
        }

        List<Integer> permissionIds = dto.getPermissionIds() == null
                ? Collections.emptyList()
                : dto.getPermissionIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()) && permissionIds.isEmpty()) {
            throw new BusinessException("管理员角色至少保留一项权限");
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, id));

        for (Integer permissionId : permissionIds) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
    }

    @Override
    @Transactional
    public void updateRoleStatus(Integer id, String status) {
        Role role = requireRole(id);
        String normalized = normalizeStatus(status);
        if (ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode()) && ROLE_STATUS_DISABLED.equals(normalized)) {
            throw new BusinessException("管理员角色不可停用");
        }

        this.update(new LambdaUpdateWrapper<Role>()
                .eq(Role::getId, id)
                .set(Role::getStatus, normalized)
                .set(Role::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    @Transactional
    public void deleteRole(Integer id) {
        Role role = requireRole(id);
        if (ADMIN_ROLE_CODE.equalsIgnoreCase(role.getRoleCode())) {
            throw new BusinessException("管理员角色不可删除");
        }

        long rosterCount = employeeRosterMapper.selectCount(new LambdaQueryWrapper<EmployeeRoster>()
                .eq(EmployeeRoster::getRoleCode, role.getRoleCode()));
        long userCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRoleCode, role.getRoleCode()));
        if (rosterCount > 0 || userCount > 0) {
            throw new BusinessException("当前角色仍有用户在使用，不能直接删除");
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, id));
        this.removeById(id);
    }

    private LambdaQueryWrapper<Role> buildQueryWrapper(RoleQueryDTO queryDTO) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        String keyword = trimToNull(queryDTO.getKeyword());
        String status = trimToNull(queryDTO.getStatus());
        if (keyword != null) {
            wrapper.and(item -> item.like(Role::getRoleName, keyword).or().like(Role::getRoleCode, keyword));
        }
        if (status != null) {
            wrapper.eq(Role::getStatus, normalizeStatus(status));
        }
        wrapper.orderByAsc(Role::getCreatedAt, Role::getId);
        return wrapper;
    }

    private List<RoleVO> buildRoleVOList(List<Role> roles, boolean includePermissionIds) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());
        List<String> roleCodes = roles.stream().map(Role::getRoleCode).collect(Collectors.toList());

        java.util.Map<Integer, List<Integer>> permissionIdsByRoleId = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds))
                .stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId,
                        LinkedHashMap::new,
                        Collectors.mapping(RolePermission::getPermissionId, Collectors.toList())));

        java.util.Map<String, List<EmployeeRoster>> activeUsersByRoleCode = employeeRosterMapper.selectList(
                        new LambdaQueryWrapper<EmployeeRoster>()
                                .in(EmployeeRoster::getRoleCode, roleCodes)
                                .eq(EmployeeRoster::getStatus, "在职"))
                .stream()
                .collect(Collectors.groupingBy(EmployeeRoster::getRoleCode,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<RoleVO> result = new ArrayList<>();
        for (Role role : roles) {
            List<Integer> permissionIds = permissionIdsByRoleId.getOrDefault(role.getId(), Collections.emptyList());
            List<EmployeeRoster> users = activeUsersByRoleCode.getOrDefault(role.getRoleCode(), Collections.emptyList());

            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setRoleName(role.getRoleName());
            vo.setRoleCode(role.getRoleCode());
            vo.setDescription(role.getDescription());
            vo.setStatus(role.getStatus());
            vo.setCreatedAt(role.getCreatedAt());
            vo.setUpdatedAt(role.getUpdatedAt());
            vo.setPermissionCount(permissionIds.size());
            vo.setUserCount(users.size());
            vo.setUserNames(users.stream()
                    .map(EmployeeRoster::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList()));
            if (includePermissionIds) {
                vo.setPermissionIds(permissionIds);
            }
            result.add(vo);
        }
        return result;
    }

    private RoleOptionVO toRoleOptionVO(Role role) {
        RoleOptionVO vo = new RoleOptionVO();
        vo.setId(role.getId());
        vo.setRoleName(role.getRoleName());
        vo.setRoleCode(role.getRoleCode());
        vo.setStatus(role.getStatus());
        return vo;
    }

    private Role requireRole(Integer id) {
        Role role = this.getById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    private void ensureRoleCodeUnique(Integer id, String roleCode) {
        Role existing = this.getOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode)
                .last("LIMIT 1"));
        if (existing != null && !Objects.equals(existing.getId(), id)) {
            throw new BusinessException("角色编码已存在");
        }
    }

    private void ensureAdminRoleImmutable(Role current, String nextRoleCode) {
        if (ADMIN_ROLE_CODE.equalsIgnoreCase(current.getRoleCode())
                && !ADMIN_ROLE_CODE.equalsIgnoreCase(nextRoleCode)) {
            throw new BusinessException("管理员角色编码不可修改");
        }
    }

    private String normalizeRoleCode(String value) {
        String roleCode = normalizeText(value, "角色编码不能为空").toUpperCase();
        if (roleCode.length() > 50) {
            throw new BusinessException("角色编码长度不能超过50");
        }
        return roleCode;
    }

    private String normalizeStatus(String value) {
        String status = trimToNull(value);
        if (status == null) {
            return ROLE_STATUS_ENABLED;
        }
        String normalized = status.toUpperCase();
        if (!ROLE_STATUS_ENABLED.equals(normalized) && !ROLE_STATUS_DISABLED.equals(normalized)) {
            throw new BusinessException("角色状态非法");
        }
        return normalized;
    }

    private String normalizeText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(message);
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
