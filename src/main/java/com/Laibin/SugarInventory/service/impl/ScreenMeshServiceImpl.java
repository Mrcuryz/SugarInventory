package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.mapper.ScreenMeshMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.ScreenMeshService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScreenMeshServiceImpl implements ScreenMeshService, LoggableService<ScreenMesh> {

    @Autowired
    private ScreenMeshMapper screenMeshMapper;

    @Override
    public List<ScreenMesh> findScreenMeshes(String meshName) {
        return screenMeshMapper.findByMeshName(meshName);
    }

    @Override
    public List<ScreenMesh> findAllScreenMesh() {
        return screenMeshMapper.findAll();
    }

    @Override
    public ScreenMesh addScreenMesh(ScreenMesh screenMesh, Integer userId) {
        if(screenMeshMapper.existByName(screenMesh.getMeshName()) != null)
            throw new BusinessException("筛网名称已存在");

        screenMesh.setCreatedBy(userId);
        screenMesh.setCreatedAt(LocalDateTime.now());
        int result = screenMeshMapper.insertScreenMesh(screenMesh);
        if (result < 1) {
            throw new BusinessException("创建筛网失败");
        }
        return screenMesh;
    }

    @Override
    public ScreenMesh updateScreenMesh(ScreenMesh screenMesh, Integer userId) {
        screenMesh.setUpdatedBy(userId);
        screenMesh.setUpdatedAt(LocalDateTime.now());
        int result = screenMeshMapper.updateScreenMesh(screenMesh);
        if (result < 1) {
            throw new BusinessException("更新筛网失败");
        }

        return screenMeshMapper.selectById(screenMesh.getId());
    }

    @Override
    public void deleteScreenMesh(Integer id) {
        int result = screenMeshMapper.deleteScreenMesh(id);
        if (result < 1) {
            throw new BusinessException("删除筛网失败");
        }
    }

    @Override
    public ScreenMesh findById(Integer id) {
        return screenMeshMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "screen_mesh";
    }
}