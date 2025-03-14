package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AssayQueryDTO;
import com.Laibin.SugarInventory.domain.dto.AssaySubmitDTO;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.AssayVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Mapper
public interface AssayService extends IService<Assay> {
    @Transactional
    void importAssays(List<AssaySubmitDTO> dtos, Integer operatorId);

    PageResult<AssayVO> queryAssays(AssayQueryDTO query);

    @Transactional
    AssayVO updateAssay(Integer id, AssaySubmitDTO dto, User operator) throws JsonProcessingException;

}
