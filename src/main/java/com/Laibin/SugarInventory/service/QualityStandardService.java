package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.QualityStandardDTO;
import com.Laibin.SugarInventory.domain.vo.QualityStandardVO;

import java.util.List;

public interface QualityStandardService {
    List<QualityStandardVO> listQualityStandards(String productType, String standardName, String status);

    PageResult<QualityStandardVO> pageQualityStandards(String productType, String standardName, String status, Integer page, Integer size);

    QualityStandardVO getQualityStandardById(Integer id);

    QualityStandardVO addQualityStandard(QualityStandardDTO dto);

    QualityStandardVO updateQualityStandard(Integer id, QualityStandardDTO dto);

    void deleteQualityStandard(Integer id);

    void forceDeleteQualityStandard(Integer id);
}
