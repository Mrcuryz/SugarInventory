package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.po.ScreenMesh;

import java.util.List;

public interface ScreenMeshService {

    // 查询筛网列表
    List<ScreenMesh> findScreenMeshes(String meshName);

    // 根据ID查询筛网
    List<ScreenMesh> findAllScreenMesh();

    // 添加筛网
    ScreenMesh addScreenMesh(ScreenMesh screenMesh, Integer userId);

    // 更新筛网
    ScreenMesh updateScreenMesh(ScreenMesh screenMesh, Integer userId);

    // 删除筛网
    void deleteScreenMesh(Integer id);
}