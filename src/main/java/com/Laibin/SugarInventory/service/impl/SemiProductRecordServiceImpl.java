package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.SemiProductRecord;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.Laibin.SugarInventory.domain.dto.RecordUpdateDTO;
import com.Laibin.SugarInventory.domain.dto.SemiProductRecordDTO;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.SemiProductRecordMapper;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
@RequiredArgsConstructor
public class SemiProductRecordServiceImpl extends ServiceImpl<SemiProductRecordMapper, SemiProductRecord> implements SemiProductRecordService, LoggableService<SemiProductRecord> {
    @Autowired
    private SemiProductRecordMapper recordMapper;
    @Autowired
    private ProductMapper productMapper;

    @Transactional
    public boolean addSemiProductRecord(AddSemiProductRecordDTO dto, String operator) {
        // 校验产品存在性
        Product product = productMapper.selectByName(dto.getProductName());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 构建持久化对象
        SemiProductRecord record = new SemiProductRecord();
        record.setProductId(product.getId());
        record.setQuantity(dto.getQuantity());
        record.setWeightPerPiece(product.getWeightPerPiece());
        record.setOperationDate(LocalDate.now());  // 自动记录操作日期
        record.setOperator(operator);              // 从登录用户获取
        record.setCreatedAt(LocalDateTime.now());  // 自动记录创建时间

        // 插入记录
        try {
            int rows = recordMapper.insert(record);
            if (rows <= 0) {
                throw new BusinessException(ErrorCode.RECORD_CREATE_FAILED);
            }
            return true;
        } catch (BusinessException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<RecordDetailVO> getRecordsByOperator(String openid, LocalDate date) {
        List<RecordDetailVO> records = recordMapper.selectByOperator(openid, date);
        records.forEach(record -> {
            record.calculateTotalWeight();
            if(Objects.equals(record.getTotalWeight(), BigDecimal.ZERO)){
                throw new BusinessException(ErrorCode.ZERO_WEIGHT_RECORD);
            }
        });

        return records;
    }

    @Override
    @Transactional
    public SemiProductRecord updateRecord(RecordUpdateDTO vo, String operator) {
        // 1. 查询原记录（验证存在性）
        SemiProductRecord origin = recordMapper.selectById(vo.getId());
        if (origin == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        // 2. 验证操作员一致性
        if (!origin.getOperator().equals(operator)) {
            throw new BusinessException(ErrorCode.OPERATION_FORBIDDEN);
        }

        // 3. 执行更新（带修改次数限制）
        int rows = recordMapper.updateWithLimit(
                vo.getId(),
                vo.getQuantity(),
                operator
        );

        // 4. 处理更新结果
        if (rows == 0) {
            throw new BusinessException(ErrorCode.MODIFY_LIMIT_EXCEEDED);
        }

        // 5. 返回更新后的记录
        return recordMapper.selectById(vo.getId());
    }

    @Override
    public List<SemiProductRecord> getSemiProductRecords(SemiProductRecordDTO vo) {
        String productName = vo.getProductName();
        LocalDate date = vo.getDate();
        String operatorName = vo.getOperatorName();
        return recordMapper.getRecordsByConditions(productName, date, operatorName);
    }

    @Override
    public RecordDetailVO getSemiProductRecord(Integer id) {
        RecordDetailVO vo =recordMapper.selectSemiProductRecordById(id);
        if(vo == null){
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        vo.calculateTotalWeight();
        return vo;
    }

    @Override
    public SemiProductRecord findById(Integer id) {
        return recordMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "semi_product_record";
    }
}
