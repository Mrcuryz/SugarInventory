package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupSubmitDTO;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssayGroupService extends IService<AssayGroup> {

    /**
     * 保存批量化验组
     */
    void addAssays(AssayGroupSubmitDTO dto, Integer userId);

    /**
     * 查询批量化验组数据
     */
    PageResult<AssayGroupVO> queryAssays(AssayGroupQueryDTO query);

    /**
     * 更新批量化验组数据
     */
    AssayGroup updateAssay(Integer id, AssayGroupSubmitDTO dto, User user);

    /**
     * 删除批量化验组数据
     */
    void deleteAssay(Integer id);
}
