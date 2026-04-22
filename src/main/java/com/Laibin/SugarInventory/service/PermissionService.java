package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.vo.PermissionVO;
import com.Laibin.SugarInventory.domain.po.Permission;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface PermissionService extends IService<Permission> {
    java.util.List<PermissionVO> listPermissionVOs();
}
