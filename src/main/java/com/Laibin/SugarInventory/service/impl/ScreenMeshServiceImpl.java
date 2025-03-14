package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshCreateDTO;
import com.Laibin.SugarInventory.domain.dto.ScreenMeshUpdateDTO;
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
        System.out.println("findScreenMeshes" + meshName);
        List<ScreenMesh> list = screenMeshMapper.findByMeshName(meshName);
        System.out.println(list);
        return list;
    }

    @Override
    public List<ScreenMesh> findAllScreenMesh() {
        return screenMeshMapper.findAll();
    }

    @Override
    public ScreenMesh addScreenMesh(ScreenMeshCreateDTO dto, Integer userId) {
        ScreenMesh screenMesh = new ScreenMesh();
        screenMesh.setMeshName(dto.getMeshName());
        screenMesh.setDescription(dto.getDescription());

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
    public ScreenMesh updateScreenMesh(ScreenMeshUpdateDTO dto, Integer userId) {
        ScreenMesh screenMesh = screenMeshMapper.selectById(dto.getId());
        if (screenMesh == null) {
            throw new BusinessException("筛网不存在");
        }

        if (dto.getMeshName()!= null &&!dto.getMeshName().equals(screenMesh.getMeshName())) {
            if (screenMeshMapper.existByName(dto.getMeshName()) != null)
                throw new BusinessException("筛网名称已存在");
            screenMesh.setMeshName(dto.getMeshName());
        }

        if(dto.getDescription()!= null){
            screenMesh.setDescription(dto.getDescription());
        }

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
        return "筛网";
    }
}