package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.EmployeeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.EmployeeUpdateDTO;
import com.Laibin.SugarInventory.domain.po.EmployeeRoster;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-20
 */
@Mapper
public interface EmployeeRosterMapper extends BaseMapper<EmployeeRoster> {

    @Select("SELECT COUNT(*) FROM employee_roster WHERE employee_id = #{employeeId}")
    boolean existsByEmployeeId(@Param("employeeId") String employeeId);

    @Select("SELECT * FROM employee_roster WHERE name = #{name}")
    EmployeeRoster selectByName(@Param("name") String name);

    @Select("SELECT COUNT(*) FROM employee_roster WHERE mobile = #{mobile}")
    boolean existsByMobile(@Param("mobile") String mobile);

    @Select("SELECT * FROM employee_roster WHERE mobile = #{mobile}")
    EmployeeRoster selectByMobile(@Param("mobile") String mobile);

    @Delete("DELETE FROM employee_roster WHERE status = '离职'")
    int deleteResignedEmployees();

    @Select("<script>" +
            "SELECT * FROM employee_roster " +
            "<where> " +
            "   <if test='query.employeeId != null and query.employeeId != \"\"'> AND employee_id LIKE CONCAT('%', #{query.employeeId}, '%') </if>" +
            "   <if test='query.name != null and query.name != \"\"'> AND name LIKE CONCAT('%', #{query.name}, '%') </if>" +
            "   <if test='query.mobile != null and query.mobile != \"\"'> AND mobile LIKE CONCAT('%', #{query.mobile}, '%') </if>" +
            "   <if test='query.department != null and query.department != \"\"'> AND department LIKE CONCAT('%', #{query.department}, '%') </if>" +
            "   <if test='query.status != null and query.status != \"\"'> AND status = #{query.status} </if>" +
            "   <if test='query.roleCode != null and query.roleCode != \"\"'> AND role_code = #{query.roleCode} </if>" +
            "</where> " +
            "ORDER BY created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<EmployeeRoster> selectEmployeeList(@Param("query") EmployeeQueryDTO query,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM employee_roster " +
            "<where> " +
            "   <if test='query.employeeId != null and query.employeeId != \"\"'> AND employee_id LIKE CONCAT('%', #{query.employeeId}, '%') </if>" +
            "   <if test='query.name != null and query.name != \"\"'> AND name LIKE CONCAT('%', #{query.name}, '%') </if>" +
            "   <if test='query.mobile != null and query.mobile != \"\"'> AND mobile LIKE CONCAT('%', #{query.mobile}, '%') </if>" +
            "   <if test='query.department != null and query.department != \"\"'> AND department LIKE CONCAT('%', #{query.department}, '%') </if>" +
            "   <if test='query.status != null and query.status != \"\"'> AND status = #{query.status} </if>" +
            "   <if test='query.roleCode != null and query.roleCode != \"\"'> AND role_code = #{query.roleCode} </if>" +
            "</where>" +
            "</script>")
    Long countEmployee(@Param("query") EmployeeQueryDTO query);

    @Update("<script>" +
            "UPDATE employee_roster " +
            "<set>" +
            "   <if test='dto.employeeId != null'> employee_id = #{dto.employeeId}, </if>" +
            "   <if test='dto.name != null'> name = #{dto.name}, </if>" +
            "   <if test='dto.mobile != null'> mobile = #{dto.mobile}, </if>" +
            "   <if test='dto.department != null'> department = #{dto.department}, </if>" +
            "   <if test='dto.position != null'> position = #{dto.position}, </if>" +
            "   <if test='dto.status != null'> status = #{dto.status}, </if>" +
            "   <if test='dto.roleCode != null'> role_code = #{dto.roleCode}, </if>" +
            "</set>" +
            "WHERE id = #{dto.id}" +
            "</script>")
    int updateEmployee(@Param("dto") EmployeeUpdateDTO dto);
}
