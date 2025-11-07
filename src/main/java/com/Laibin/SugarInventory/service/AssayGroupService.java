package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupSubmitDTO;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface AssayGroupService extends IService<AssayGroup> {

    /**
     * 保存化验验收标准
     *
     */
    void addAssays(AssayGroupSubmitDTO dto, Integer userId);

    /**
     * 查询化验验收标准数据
     *
     */
    PageResult<AssayGroupVO> queryAssays(AssayGroupQueryDTO query);

    /**
     * 更新化验验收标准数据
     *
     */
    AssayGroup updateAssay(Integer id, AssayGroupSubmitDTO dto, User user);

    /**
     * 删除化验验收标准数据
     *
     */
    void deleteAssay(Integer id);
}
