package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.po.SemiProductRecord;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.Laibin.SugarInventory.domain.dto.SemiProductRecordDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
public interface SemiProductRecordService extends IService<SemiProductRecord> {
    InVO addSemiProductRecord(AddSemiProductRecordDTO dto, String operator);

    List<RecordDetailVO> getRecordsByOperator(String openid, LocalDate date);

    PageResult<RecordDetailVO> getSemiProductRecords(SemiProductRecordDTO dto, User currentUser);

    List<RecordDetailVO> getSemiProductRecordsByIds(List<Integer> ids);

    RecordDetailVO getSemiProductRecord(Integer id);

    @Transactional
    InVO stackModeInStock(AddSemiProductRecordDTO dto, String operator);
}
