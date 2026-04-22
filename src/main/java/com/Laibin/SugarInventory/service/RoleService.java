package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.RolePermissionUpdateDTO;
import com.Laibin.SugarInventory.domain.dto.RoleQueryDTO;
import com.Laibin.SugarInventory.domain.dto.RoleSaveDTO;
import com.Laibin.SugarInventory.domain.po.Role;
import com.Laibin.SugarInventory.domain.vo.RoleOptionVO;
import com.Laibin.SugarInventory.domain.vo.RoleVO;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface RoleService extends IService<Role> {
    PageResult<RoleVO> queryRoles(RoleQueryDTO queryDTO);

    List<RoleOptionVO> listEnabledRoleOptions();

    RoleVO getRoleDetail(Integer id);

    RoleVO createRole(RoleSaveDTO dto);

    RoleVO updateRole(Integer id, RoleSaveDTO dto);

    void updateRolePermissions(Integer id, RolePermissionUpdateDTO dto);

    void updateRoleStatus(Integer id, String status);

    void deleteRole(Integer id);
}
