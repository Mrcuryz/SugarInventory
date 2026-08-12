package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayGroupQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssayGroupSubmitDTO;
import com.Laibin.SugarInventory.domain.po.AssayGroup;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayGroupVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

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
     * 按产品 ID 精确查询当前批量化验组配置。
     */
    List<AssayGroup> listByProductId(Integer productId);

    /**
     * 更新批量化验组数据
     */
    AssayGroupVO updateAssay(Integer id, AssayGroupSubmitDTO dto, User user);

    /**
     * 删除批量化验组数据
     */
    void deleteAssay(Integer id);
}
