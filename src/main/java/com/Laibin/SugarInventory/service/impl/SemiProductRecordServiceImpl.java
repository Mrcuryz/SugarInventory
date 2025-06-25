package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.Laibin.SugarInventory.mapper.*;
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
import java.util.*;
import java.util.stream.Collectors;

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
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private AssayMapper assayMapper;
    @Autowired
    private InventoryMapper inventoryMapper;

    @Transactional
    public InVO addSemiProductRecord(AddSemiProductRecordDTO dto, String operator) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 获取库位信息
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        Assay assay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        if (assay == null) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }

        int maxRows = warehouse.getMaxRows();
        int remainingQuantity = dto.getQuantity();
        String currentSide = dto.getSide(); // 默认从左侧存放
        boolean canStack = product.getCanStack(); // 是否可堆积

        // **3. 预获取当前库位的存储情况**
        int leftUsedRowsLayer1 = inventoryMapper.getUsedRows(warehouse.getId(), "左", 1);
        int rightUsedRowsLayer1 = inventoryMapper.getUsedRows(warehouse.getId(), "右", 1);
        int leftUsedRowsLayer2 = inventoryMapper.getUsedRows(warehouse.getId(), "左", 2);
        int rightUsedRowsLayer2 = inventoryMapper.getUsedRows(warehouse.getId(), "右", 2);

        int currentLayer = (leftUsedRowsLayer2 > 0 || rightUsedRowsLayer2 > 0) ? 2 : 1;

        // 计算库位剩余容量
        int remainingCapacity = 2 * maxRows - leftUsedRowsLayer1 - rightUsedRowsLayer1;
        if(product.getCanStack() && currentLayer == 1) {
            remainingCapacity += 2 * maxRows;
        }

        if(remainingCapacity <= 0){
            throw new BusinessException(ErrorCode.WAREHOUSE_FULL);
        }

        int quantity = dto.getQuantity() > remainingCapacity? remainingCapacity : dto.getQuantity();

        BigDecimal totalWeight = product.getWeightPerPiece()
                .multiply(new BigDecimal(quantity)
                        .multiply(new BigDecimal(product.getPiecesPerPallet())));

        // **4. 记录入库信息**
        SemiProductRecord semiProductRecord = new SemiProductRecord();
        semiProductRecord.setWarehouseId(warehouse.getId());
        semiProductRecord.setProductId(dto.getProductId());
        semiProductRecord.setQuantity(quantity);
        semiProductRecord.setScreenMeshId(dto.getScreenMeshId());
        semiProductRecord.setOperator(operator);
        semiProductRecord.setOperationDate(dto.getEntryDate());
        semiProductRecord.setAssayId(assay.getId());
        semiProductRecord.setTotalWeight(totalWeight);
        semiProductRecord.setCreatedAt(LocalDateTime.now());

        recordMapper.insert(semiProductRecord);

        Integer inStockId = semiProductRecord.getId();

        // **5. 开始存放**
        while (remainingQuantity > 0) {
            int usedRows = (currentSide.equals("左")) ?
                    (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
                    : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);

            if (usedRows < maxRows) {
                int rowNumber = usedRows + 1;

                // **存储单板**
                Inventory inventory = new Inventory();
                inventory.setWarehouseId(warehouse.getId());
                inventory.setProductId(dto.getProductId());
                inventory.setSide(currentSide);
                inventory.setRowNumber(rowNumber);
                inventory.setLayer(currentLayer);
                inventory.setQuantity(1);
                inventory.setScreenMeshId(dto.getScreenMeshId());
                inventory.setEntryDate(dto.getEntryDate());
                inventory.setAssayId(assay.getId());
                inventory.setProductStatus(product.getStatus());
                inventory.setCreatedAt(LocalDateTime.now());

                inventoryMapper.insert(inventory);
                remainingQuantity--;

                // **更新本地变量**
                if (currentSide.equals("左")) {
                    if (currentLayer == 1) leftUsedRowsLayer1++;
                    else leftUsedRowsLayer2++;
                } else {
                    if (currentLayer == 1) rightUsedRowsLayer1++;
                    else rightUsedRowsLayer2++;
                }
            }

            // **如果当前列满，尝试切换到另一侧**
            if (remainingQuantity > 0 && usedRows >= maxRows) {
                currentSide = currentSide.equals("左") ? "右" : "左";
                usedRows = (currentSide.equals("左")) ?
                        (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
                        : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
            }

            // **如果第一层满了，检查是否可以堆积**
            if (remainingQuantity > 0 && leftUsedRowsLayer1 >= maxRows && rightUsedRowsLayer1 >= maxRows) {
                if (canStack && currentLayer == 1) {
                    // **切换到第二层**
                    currentLayer = 2;
                    leftUsedRowsLayer2 = 0;
                    rightUsedRowsLayer2 = 0;
                } else if (!canStack || (leftUsedRowsLayer2 >= maxRows && rightUsedRowsLayer2 >= maxRows)) {
                    // **如果当前是第二层且满了，则提示库位已满**
                    warehouseMapper.updateCurCapacity(
                            warehouse.getId(), warehouse.getCurCapacity() + quantity);
                    if(product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
                        warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);

                    InVO inVO = new InVO();
                    inVO.setRemainingQuantity(remainingQuantity);
                    inVO.setMessage("库位已满！剩余 " + remainingQuantity + " 板产品，请选择新库位");
                    return inVO;
                }
            }
        }

        // **6. 同步更新库位信息**
        warehouseMapper.updateCurCapacity(
                warehouse.getId(), warehouse.getCurCapacity() + quantity);
        if(product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);

        // **7. 返回入库信息**
        InVO inVO = new InVO();
        inVO.setRemainingQuantity(remainingQuantity);
        inVO.setMessage("入库成功！");
        return inVO;
    }

    @Override
    @Transactional
    public InVO stackModeInStock(AddSemiProductRecordDTO dto, String operator) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);

        Assay assay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        if (assay == null) throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);

        int maxRows = warehouse.getMaxRows();
        int maxCapacity = maxRows * 2;
        int curCapacity = warehouse.getCurCapacity();
        int available = maxCapacity - curCapacity;

        if (available <= 0) throw new BusinessException(ErrorCode.WAREHOUSE_FULL);

        int inQty = Math.min(dto.getQuantity(), available);
        int remaining = dto.getQuantity() - inQty;

        // 记录入库记录
        BigDecimal totalWeight = product.getWeightPerPiece()
                .multiply(new BigDecimal(inQty).multiply(new BigDecimal(product.getPiecesPerPallet())));
        SemiProductRecord record = new SemiProductRecord();
        record.setWarehouseId(warehouse.getId());
        record.setProductId(dto.getProductId());
        record.setQuantity(inQty);
        record.setScreenMeshId(dto.getScreenMeshId());
        record.setOperator(operator);
        record.setOperationDate(dto.getEntryDate());
        record.setAssayId(assay.getId());
        record.setTotalWeight(totalWeight);
        record.setCreatedAt(LocalDateTime.now());

        recordMapper.insert(record);

        // 计算当前最大 row_number
        List<Inventory> existList = inventoryMapper.selectByWarehouseOrdered(warehouse.getId());
        Set<String> occupied = existList.stream()
                .map(inv -> inv.getSide() + "-" + inv.getRowNumber())
                .collect(Collectors.toSet());

        int count = 0;

        // 入库顺序：左1～左N → 右1～右N
        for (String side : List.of("左", "右")) {
            for (int row = 1; row <= maxRows && count < inQty; row++) {
                String key = side + "-" + row;
                if (occupied.contains(key)) continue;

                Inventory inv = new Inventory();
                inv.setWarehouseId(warehouse.getId());
                inv.setProductId(dto.getProductId());
                inv.setSide(side);
                inv.setRowNumber(row);
                inv.setLayer(1); // 固定为第一层
                inv.setQuantity(1);
                inv.setScreenMeshId(dto.getScreenMeshId());
                inv.setEntryDate(dto.getEntryDate());
                inv.setAssayId(assay.getId());
                inv.setProductStatus(product.getStatus());
                inv.setCreatedAt(LocalDateTime.now());

                inventoryMapper.insert(inv);
                count++;
            }
        }

        // 更新仓库当前容量
        warehouseMapper.updateCurCapacity(warehouse.getId(), curCapacity + inQty);

        InVO vo = new InVO();
        vo.setRemainingQuantity(remaining);
        vo.setMessage(remaining > 0 ? "已入库 " + inQty + " 板，库位已满，剩余 " + remaining + " 板未入库" : "入库成功");
        return vo;
    }

    @Override
    public List<RecordDetailVO> getRecordsByOperator(String openid, LocalDate date) {
        return recordMapper.selectByOperator(openid, date);
    }

    @Override
    public List<RecordDetailVO> getSemiProductRecordsByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return recordMapper.selectSemiProductRecordsByIds(ids);
    }

    @Override
    public PageResult<RecordDetailVO> getSemiProductRecords(SemiProductRecordDTO dto, User currentUser) {
        int offset = (dto.getPage() - 1) * dto.getSize();

        boolean isStaff = currentUser.getRoleCode().equals("STAFF");

        List<RecordDetailVO> recordList = recordMapper.getRecordsByConditions(
                dto, offset, dto.getSize(), isStaff, currentUser.getName()
        );

        for (RecordDetailVO record : recordList) {
            if(isStaff){
                record.setAssayId(null);
                record.setSampleDate(null);
                record.setColorValue(null);
                record.setReducingSugar(null);
                record.setDryWeight(null);
                record.setConductivityAsh(null);
                record.setSucrose(null);
                record.setInsolubleImpurity(null);
                record.setPhValue(null);
                record.setTesterName(null);
                record.setIsQualified(null);
                record.setQualifiedStandards(null);
            }
        }

        Long total = recordMapper.countByConditions(dto, isStaff, currentUser.getName());
        return new PageResult<>(total, recordList);
    }

    @Override
    public RecordDetailVO getSemiProductRecord(Integer id) {
        RecordDetailVO vo =recordMapper.selectSemiProductRecordById(id);
        if(vo == null){
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

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
