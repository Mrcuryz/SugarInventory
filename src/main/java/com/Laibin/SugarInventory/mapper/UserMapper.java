package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.Laibin.SugarInventory.domain.po.Permission;
import org.apache.ibatis.annotations.Update;

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
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM user WHERE openid = #{openid}")
    User selectByOpenid(@Param("openid") String openid);

    @Select("SELECT u.*, r.role_code " +
            "FROM user u " +
            "LEFT JOIN role r ON u.role_code = r.role_code " +
            "WHERE u.openid = #{openid}")
    User selectByOpenidWithRole(@Param("openid") String openid);

    @Select("SELECT * FROM user WHERE employee_id = #{employeeId}")
    User selectByEmployeeId(@Param("employeeId") String employeeId);

    @Update("UPDATE user SET role_code = #{roleCode} WHERE employee_id = #{employeeId}")
    void updateRoleByEmployeeId(@Param("employeeId") String employeeId, @Param("roleCode") String roleCode);
}
