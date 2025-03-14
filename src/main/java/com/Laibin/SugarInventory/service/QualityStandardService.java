package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.po.QualityStandard;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;

import java.util.List;

public interface QualityStandardService {
    /**
     * 获取所有检验标准（可按产品类型筛选）
     */
    List<QualityStandardVO> listQualityStandards(String productType, String standardName);

    /**
     * 根据 ID 查询标准
     */
    QualityStandardVO getQualityStandardById(Integer id);

    /**
     * 添加新的检验标准
     */
    QualityStandard addQualityStandard(QualityStandardDTO dto);

    /**
     * 更新检验标准
     */
    QualityStandard updateQualityStandard(Integer id, QualityStandardDTO dto);

    /**
     * 删除检验标准
     */
    void deleteQualityStandard(Integer id);
}
