package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScreenMeshMapper extends BaseMapper<ScreenMesh> {
    // 查询筛网列表
    @Select("SELECT * FROM screen_mesh WHERE mesh_name LIKE CONCAT('%', #{meshName}, '%')")
    List<ScreenMesh> findByMeshName(@Param("meshName") String meshName);

    @Select("SELECT * FROM screen_mesh WHERE mesh_name = #{meshName}")
    ScreenMesh existByName(@Param("meshName") String meshName);

    @Select("SELECT * FROM screen_mesh")
    List<ScreenMesh> findAll();

    // 插入筛网
    @Insert("INSERT INTO screen_mesh (mesh_name, description, created_by, created_at) " +
            "VALUES (#{meshName}, #{description}, #{createdBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertScreenMesh(ScreenMesh screenMesh);

    // 更新筛网
    @Update("UPDATE screen_mesh SET mesh_name = #{meshName}, description = #{description}, updated_by = #{updatedBy}, updated_at = NOW() WHERE id = #{id}")
    int updateScreenMesh(ScreenMesh screenMesh);

    // 删除筛网
    @Delete("DELETE FROM screen_mesh WHERE id = #{id}")
    int deleteScreenMesh(@Param("id") Integer id);
}
