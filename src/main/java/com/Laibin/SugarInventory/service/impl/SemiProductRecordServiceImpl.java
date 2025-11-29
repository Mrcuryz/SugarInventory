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
 * 服务实现类
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
        // 计算总重量
        BigDecimal totalWeight;
        // 存入板数
        InVO inVO;
        Integer quantity = dto.getQuantity();
        if (dto.getUnit().equals("0")) {
            totalWeight = new BigDecimal(quantity)
                    .multiply(BigDecimal.valueOf(product.getPiecesPerPallet()))
                    .multiply(product.getWeightPerPiece());
            // 整版入库
            inVO = this.handlerInStock(dto, product, warehouse, assay, false, 0);
        } else {
            totalWeight = new BigDecimal(quantity)
                    .multiply(product.getWeightPerPiece());
            // 总散件数
            inVO = this.handlerInStockPieces(dto, product, warehouse, assay);
        }
        // **4. 记录入库信息**
        SemiProductRecord semiProductRecord = new SemiProductRecord();
        semiProductRecord.setWarehouseId(warehouse.getId());
        semiProductRecord.setProductId(dto.getProductId());
        semiProductRecord.setQuantity(quantity);
        semiProductRecord.setScreenMeshId(product.getScreenMeshId());
        semiProductRecord.setOperator(operator);
        semiProductRecord.setOperationDate(dto.getEntryDate());
        semiProductRecord.setAssayId(assay.getId());
        semiProductRecord.setTotalWeight(totalWeight);
        semiProductRecord.setCreatedAt(LocalDateTime.now());
        // 散件凑一板
        semiProductRecord.setUnit(dto.getUnit());
        recordMapper.insert(semiProductRecord);
        return inVO;
    }


    @Override
    public InVO handlerInStockPieces(BaseInStockDTO dto, Product product, Warehouse warehouse, Assay assay) {
        Integer quantity = dto.getQuantity();
        // 每板件数
        Integer piecesPerPallet = product.getPiecesPerPallet();
        // 先把存在的散件的行数补齐，再入库整行
        // 获取货架上存在散件的行数
        List<Inventory> hasPiecesRows = inventoryMapper.getHasPiecesRows(warehouse.getId(), dto.getEntryDate(),
                product.getId(), piecesPerPallet);
        for (Inventory inventory : hasPiecesRows) {
            // 可额外容纳件数
            int canAddPieces = piecesPerPallet - inventory.getPieces();
            if (quantity > canAddPieces) {
                inventory.setPieces(piecesPerPallet);
            } else {
                inventory.setPieces(inventory.getPieces() + quantity);
            }
            quantity = quantity - canAddPieces;
            inventoryMapper.updatePieces(inventory.getId(), inventory.getPieces());
            if (quantity < 0) {
                return InVO.createDefault();
            }
        }

        // 散件板数
        int boardNum = quantity / piecesPerPallet;
        // 剩余散件数
        int piecesNum = quantity % piecesPerPallet;
        if (boardNum > 0) {
            dto.setQuantity(boardNum);
            //存放散件整板
            InVO inVO = this.handlerInStock(dto, product, warehouse, assay, true, piecesPerPallet);
            if (inVO.getRemainingQuantity() > 0) {
                inVO.setRemainingQuantity(inVO.getRemainingQuantity() + 1);
                return inVO;
            }
            // 这里重新获取库位信息，因为cur_capacity当前容量已经更新了
            warehouse = warehouseMapper.selectById(warehouse.getId());
        }
        // 货架散件补满后，再开一行放剩余散件
        if (piecesNum > 0) {
            dto.setQuantity(1);
            return this.handlerInStock(dto, product, warehouse, assay, true, piecesNum);
        }
        return InVO.createDefault();
    }

    /**
     * 存板数
     */
    @Override
    public InVO handlerInStock(BaseInStockDTO dto, Product product, Warehouse warehouse,
                               Assay assay, Boolean isPieces, Integer piecesNum) {
        int maxRows = warehouse.getMaxRows();
        int remainingQuantity = dto.getQuantity();
        String currentSide = dto.getSide(); // 默认从左侧存放
        boolean canStack = product.getCanStack(); // 是否可堆积

        // **3. 预获取当前库位的存储情况**
        List<Integer> leftUsedRowList = inventoryMapper.getUsedRowList(warehouse.getId(), "左", 1);
        List<Integer> leftUnusedRowList = this.getUnusedRowNumbers(leftUsedRowList, maxRows);
        int leftUsedRowsLayer1 = leftUsedRowList.size();
        List<Integer> rightUsedRowList = inventoryMapper.getUsedRowList(warehouse.getId(), "右", 1);
        List<Integer> rightUnusedRowList = this.getUnusedRowNumbers(rightUsedRowList, maxRows);
        int rightUsedRowsLayer1 = rightUsedRowList.size();
        List<Integer> leftUsedRow2List = inventoryMapper.getUsedRowList(warehouse.getId(), "左", 2);
        List<Integer> leftUnusedRow2List = this.getUnusedRowNumbers(leftUsedRow2List, maxRows);
        int leftUsedRowsLayer2 = leftUsedRow2List.size();
        List<Integer> rightUsedRow2List = inventoryMapper.getUsedRowList(warehouse.getId(), "右", 2);
        int rightUsedRowsLayer2 = rightUsedRow2List.size();
        List<Integer> rightUnusedRow2List = this.getUnusedRowNumbers(rightUsedRow2List, maxRows);

        int currentLayer = leftUnusedRowList.isEmpty() && rightUnusedRowList.isEmpty() ? 2 : 1;

        // 计算库位剩余容量
        int remainingCapacity = 2 * maxRows - leftUsedRowsLayer1 - rightUsedRowsLayer1
                - leftUsedRowsLayer2 - rightUsedRowsLayer2;
        if (canStack) {
            remainingCapacity += 2 * maxRows;
        }

        if (remainingCapacity <= 0) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FULL);
        }

        int quantity = dto.getQuantity() > remainingCapacity ? remainingCapacity : dto.getQuantity();
        boolean isCheckSite = false;
        // **5. 开始存放**
        while (remainingQuantity > 0) {
            Integer rowNumber = this.createRowNumber(leftUnusedRowList, rightUnusedRowList,
                    leftUnusedRow2List, rightUnusedRow2List,
                    currentSide, currentLayer);
            if (rowNumber != null) {
                // **存储单板**
                Inventory inventory = new Inventory();
                inventory.setWarehouseId(warehouse.getId());
                inventory.setProductId(dto.getProductId());
                inventory.setSide(currentSide);
                inventory.setRowNumber(rowNumber);
                inventory.setLayer(currentLayer);
                inventory.setQuantity(1);
                inventory.setScreenMeshId(product.getScreenMeshId());
                inventory.setEntryDate(dto.getEntryDate());
                inventory.setAssayId(assay.getId());
                inventory.setProductStatus(product.getStatus());
                inventory.setCreatedAt(LocalDateTime.now());
                inventory.setInStockId(dto.getInStockId());
                // 散件凑一板
                if (isPieces) {
                    inventory.setPieces(piecesNum);
                } else {
                    inventory.setPieces(0);
                }

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
            if (remainingQuantity > 0 && rowNumber == null && !isCheckSite) {
                currentSide = currentSide.equals("左") ? "右" : "左";
                isCheckSite = true;
            }

            // **如果第一层满了，检查是否可以堆积**
            if (remainingQuantity > 0 && leftUsedRowsLayer1 >= maxRows && rightUsedRowsLayer1 >= maxRows) {
                if (canStack && currentLayer == 1) {
                    // **切换到第二层**
                    isCheckSite = false;
                    currentLayer = 2;
                    leftUsedRowsLayer2 = 0;
                    rightUsedRowsLayer2 = 0;
                } else if (!canStack || (leftUsedRowsLayer2 >= maxRows && rightUsedRowsLayer2 >= maxRows)) {
                    // **如果当前是第二层且满了，则提示库位已满**
                    warehouseMapper.updateCurCapacity(
                            warehouse.getId(), warehouse.getCurCapacity() + quantity);
                    if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
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
        if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
        // **7. 返回入库信息**
        InVO inVO = new InVO();
        inVO.setRemainingQuantity(remainingQuantity);
        inVO.setMessage("入库成功！");
        return inVO;
    }

    private Integer createRowNumber(List<Integer> leftUsedRowList,
                                    List<Integer> rightUsedRowList,
                                    List<Integer> leftUsedRow2List,
                                    List<Integer> rightUsedRow2List,
                                    String currentSide, Integer currentLayer) {
        if (currentSide.equals("左")) {
            if (currentLayer == 1) {
                if (leftUsedRowList.isEmpty()) {
                    return null;
                }
                Integer rowNumber = leftUsedRowList.get(0);
                leftUsedRowList.remove(0);
                return rowNumber;
            } else {
                if (leftUsedRow2List.isEmpty()) {
                    return null;
                }
                Integer rowNumber = leftUsedRow2List.get(0);
                leftUsedRow2List.remove(0);
                return rowNumber;
            }
        } else {
            if (currentLayer == 1) {
                if (rightUsedRowList.isEmpty()) {
                    return null;
                }
                Integer rowNumber = rightUsedRowList.get(0);
                rightUsedRowList.remove(0);
                return rowNumber;
            } else {
                if (rightUsedRow2List.isEmpty()) {
                    return null;
                }
                Integer rowNumber = rightUsedRow2List.get(0);
                rightUsedRow2List.remove(0);
                return rowNumber;
            }
        }
    }

    private List<Integer> getUnusedRowNumbers(List<Integer> usedRowNumbers, int maxRows) {
        List<Integer> unusedRowNumbers = new ArrayList<>();
        for (int i = 1; i <= maxRows; i++) {
            if (!usedRowNumbers.contains(i)) {
                unusedRowNumbers.add(i);
            }
        }
        return unusedRowNumbers;
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

        // 计算总重量
        BigDecimal totalWeight;
        // 存入板数
        InVO vo = new InVO();
        Integer quantity = dto.getQuantity();
        if (dto.getUnit().equals("0")) {
            totalWeight = new BigDecimal(quantity)
                    .multiply(BigDecimal.valueOf(product.getPiecesPerPallet()))
                    .multiply(product.getWeightPerPiece());
            // 整版入库
            Integer remaining = this.stackModeInStockQuantity(dto, warehouse, assay, product, false, 0);
            vo.setRemainingQuantity(remaining);
            vo.setMessage(remaining > 0 ? "库位已满，剩余 " + remaining + " 板未入库" : "入库成功");
        } else {
            totalWeight = new BigDecimal(quantity)
                    .multiply(product.getWeightPerPiece());
            // 总散件数
            Integer modeInStockPiece = this.stackModeInStockPiece(dto, warehouse, assay, product);
            vo.setRemainingQuantity(modeInStockPiece);
            vo.setMessage(modeInStockPiece > 0 ? "库位已满，剩余 " + modeInStockPiece + " 件未入库" : "入库成功");
        }
        SemiProductRecord record = new SemiProductRecord();
        record.setWarehouseId(warehouse.getId());
        record.setProductId(dto.getProductId());
        record.setQuantity(dto.getQuantity() - vo.getRemainingQuantity());
        record.setScreenMeshId(product.getScreenMeshId());
        record.setOperator(operator);
        record.setOperationDate(dto.getEntryDate());
        record.setAssayId(assay.getId());
        record.setTotalWeight(totalWeight);
        record.setCreatedAt(LocalDateTime.now());
        record.setUnit(dto.getUnit());
        recordMapper.insert(record);
        return vo;
    }

    private Integer stackModeInStockPiece(AddSemiProductRecordDTO dto, Warehouse warehouse, Assay assay, Product product) {
        Integer quantity = dto.getQuantity();
        // 每板件数
        Integer piecesPerPallet = product.getPiecesPerPallet();
        // 先把存在的散件的行数补齐，再入库整行
        // 获取货架上存在散件的行数
        List<Inventory> hasPiecesRows = inventoryMapper.getHasPiecesRows(warehouse.getId(), dto.getEntryDate(),
                product.getId(), piecesPerPallet);
        for (Inventory inventory : hasPiecesRows) {
            // 可额外容纳件数
            int canAddPieces = piecesPerPallet - inventory.getPieces();
            if (quantity > canAddPieces) {
                inventory.setPieces(piecesPerPallet);
            } else {
                inventory.setPieces(inventory.getPieces() + quantity);
            }
            quantity = quantity - canAddPieces;
            inventoryMapper.updatePieces(inventory.getId(), inventory.getPieces());
            if (quantity < 0) {
                return 0;
            }
        }
        // 散件板数
        int boardNum = quantity / piecesPerPallet;
        // 剩余散件数
        int piecesNum = quantity % piecesPerPallet;
        if (boardNum > 0) {
            dto.setQuantity(boardNum);
            //存放散件整板
            Integer remainingQuantity = this.stackModeInStockQuantity(dto, warehouse, assay, product, false, 0);
            if (remainingQuantity > 0) {
                return remainingQuantity * piecesPerPallet + piecesNum;
            }
            // 这里重新获取库位信息，因为cur_capacity当前容量已经更新了
            warehouse = warehouseMapper.selectById(warehouse.getId());
        }
        // 货架散件补满后，再开一行放剩余散件
        if (piecesNum > 0) {
            dto.setQuantity(1);
            Integer modeInStockQuantity = this.stackModeInStockQuantity(dto, warehouse, assay, product, true, piecesNum);
            if (modeInStockQuantity > 0) {
                return piecesNum;
            }
            return modeInStockQuantity;
        }
        return 0;
    }

    private Integer stackModeInStockQuantity(AddSemiProductRecordDTO dto, Warehouse warehouse,
                                             Assay assay, Product product, Boolean isPieces, Integer piecesNum) {
        int maxRows = warehouse.getMaxRows();
        int maxCapacity = maxRows * 2;
        int curCapacity = warehouse.getCurCapacity();
        int available = maxCapacity - curCapacity;

        if (available <= 0) throw new BusinessException(ErrorCode.WAREHOUSE_FULL);

        int inQty = Math.min(dto.getQuantity(), available);
        int remaining = dto.getQuantity() - inQty;
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
                if (isPieces) {
                    inv.setPieces(piecesNum);
                } else {
                    inv.setPieces(0);
                }
                inv.setScreenMeshId(product.getScreenMeshId());
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
        return remaining;
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
            if (isStaff) {
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
        RecordDetailVO vo = recordMapper.selectSemiProductRecordById(id);
        if (vo == null) {
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
