package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.Permission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
    @Select("SELECT p.* FROM permission p " +
            "JOIN role_permission rp ON p.id = rp.permission_id " +
            "JOIN role r ON rp.role_id = r.id " +
            "WHERE r.role_code = #{roleCode}")
    List<Permission> selectPermissionsByRoleCode(@Param("roleCode") String roleCode);
}
