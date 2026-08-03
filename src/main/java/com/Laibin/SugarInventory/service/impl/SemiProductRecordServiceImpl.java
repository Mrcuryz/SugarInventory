package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEventCommand;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementEventService;
import com.Laibin.SugarInventory.domain.vo.RecordDetailVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.Laibin.SugarInventory.service.model.PalletInventoryOccupancyRule;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
    private static final int MAX_LOCATION_RETRY = 3;

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
    @Autowired
    private StockMovementEventService stockMovementEventService;

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
        if(product.getScreenMeshId() == null){
            throw new BusinessException(ErrorCode.SCREEN_MESH_NOT_FOUND);
        }
        InVO inVO;
        Integer quantity = dto.getQuantity();
        if (dto.getUnit().equals("0")) {
            inVO = this.handlerInStock(dto, product, warehouse, assay, false, 0);
        } else {
            inVO = this.handlerInStockPieces(dto, product, warehouse, assay);
        }
        int actualQuantity = Math.max(quantity - (inVO.getRemainingQuantity() == null ? 0 : inVO.getRemainingQuantity()), 0);
        SemiProductRecord semiProductRecord = new SemiProductRecord();
        semiProductRecord.setWarehouseId(warehouse.getId());
        semiProductRecord.setProductId(dto.getProductId());
        semiProductRecord.setQuantity(actualQuantity);
        semiProductRecord.setScreenMeshId(product.getScreenMeshId());
        semiProductRecord.setOperator(operator);
        semiProductRecord.setOperationDate(dto.getEntryDate());
        semiProductRecord.setAssayId(assay == null ? null : assay.getId());
        semiProductRecord.setTotalWeight(calculateTotalWeight(product, actualQuantity, dto.getUnit()));
        semiProductRecord.setCreatedAt(LocalDateTime.now());
        semiProductRecord.setUnit(dto.getUnit());
        recordMapper.insert(semiProductRecord);
        recordSemiInboundEvent(semiProductRecord, product, warehouse, dto, actualQuantity);
        return inVO;
    }


    @Override
    public InVO handlerInStockPieces(BaseInStockDTO dto, Product product, Warehouse warehouse, Assay assay) {
        int requestedPieces = dto.getQuantity() == null ? 0 : dto.getQuantity();
        Integer piecesPerPallet = product.getPiecesPerPallet();
        PalletInventoryOccupancyRule.validatePiecesPerPallet(piecesPerPallet, "散件入库");
        if (dto.getPalletCodeId() != null) {
            PalletInventoryOccupancyRule.validateSingleQrInventory(dto.getUnit(), requestedPieces, piecesPerPallet, "二维码入库");
            dto.setQuantity(1);
            InVO inVO = this.handlerInStock(dto, product, warehouse, assay, true, requestedPieces);
            inVO.setRemainingQuantity(convertLoosePiecesRemaining(inVO, requestedPieces));
            return inVO;
        }

        PalletInventoryOccupancyRule.PieceSplit split =
                PalletInventoryOccupancyRule.splitPieces(requestedPieces, piecesPerPallet, "散件入库");
        if (split.fullPalletCount() > 0) {
            dto.setQuantity(split.fullPalletCount());
            InVO inVO = this.handlerInStock(dto, product, warehouse, assay, false, 0);
            if (inVO.getRemainingQuantity() != null && inVO.getRemainingQuantity() > 0) {
                inVO.setRemainingQuantity(inVO.getRemainingQuantity() * piecesPerPallet + split.loosePieces());
                return inVO;
            }
            warehouse = warehouseMapper.selectById(warehouse.getId());
        }
        if (split.loosePieces() > 0) {
            dto.setQuantity(1);
            InVO inVO = this.handlerInStock(dto, product, warehouse, assay, true, split.loosePieces());
            inVO.setRemainingQuantity(convertLoosePiecesRemaining(inVO, split.loosePieces()));
            return inVO;
        }
        return InVO.createDefault();
    }

    /**
     * 存板数
     */
    @Override
    public InVO handlerInStock(BaseInStockDTO dto, Product product, Warehouse warehouse,
                               Assay assay, Boolean isPieces, Integer piecesNum) {
        if (dto.getRowNumber() != null || dto.getLayer() != null) {
            return handlerSpecifiedLocationInStock(dto, product, warehouse, assay, isPieces, piecesNum);
        }
        int remainingQuantity = dto.getQuantity();
        int insertedCount = 0;
        int conflictCount = 0;
        String preferredSide = dto.getSide();
        while (remainingQuantity > 0) {
            warehouse = warehouseMapper.selectById(warehouse.getId());
            List<InventoryLocationCandidate> candidates = findAvailableLocationCandidates(
                    warehouse.getId(),
                    preferredSide,
                    warehouse.getMaxRows(),
                    Boolean.TRUE.equals(product.getCanStack())
            );
            if (candidates.isEmpty()) {
                break;
            }

            boolean inserted = false;
            for (InventoryLocationCandidate candidate : candidates) {
                Inventory inventory = buildInventory(dto, product, warehouse, assay, isPieces, piecesNum, candidate);
                try {
                inventoryMapper.insert(inventory);
                remainingQuantity--;
                    insertedCount++;
                    preferredSide = candidate.side();
                    inserted = true;
                    break;
                } catch (DuplicateKeyException e) {
                    conflictCount++;
                    if (conflictCount >= MAX_LOCATION_RETRY) {
                        throw new BusinessException("库位分配冲突，请重试");
                    }
                    break;
                }
            }
            if (!inserted && candidates.isEmpty()) {
                break;
            }
        }

        // **6. 同步更新库位信息**
        warehouseMapper.updateCurCapacity(
                warehouse.getId(), warehouse.getCurCapacity() + insertedCount);
        if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
        // **7. 返回入库信息**
        InVO inVO = new InVO();
        inVO.setRemainingQuantity(remainingQuantity);
        inVO.setMessage("入库成功！");
        return inVO;
    }

    private InVO handlerSpecifiedLocationInStock(BaseInStockDTO dto, Product product, Warehouse warehouse,
                                                Assay assay, Boolean isPieces, Integer piecesNum) {
        if (dto.getRowNumber() == null || dto.getLayer() == null) {
            throw new BusinessException("指定入库位置必须同时包含层数和排号");
        }
        if (!Integer.valueOf(1).equals(dto.getQuantity())) {
            throw new BusinessException("指定位置入库仅支持单板操作");
        }
        if (dto.getRowNumber() < 1 || dto.getRowNumber() > warehouse.getMaxRows()) {
            throw new BusinessException("目标排号超出库位范围");
        }
        if (dto.getLayer() < 1 || dto.getLayer() > 2) {
            throw new BusinessException("目标层数非法");
        }
        if (dto.getLayer() == 2 && !Boolean.TRUE.equals(product.getCanStack())) {
            throw new BusinessException("当前产品不支持二层堆放");
        }
        List<Integer> usedRows = inventoryMapper.getUsedRowListForUpdate(warehouse.getId(), dto.getSide(), dto.getLayer());
        if (usedRows.contains(dto.getRowNumber())) {
            throw new BusinessException("目标位置已有库存，不能入库");
        }

        InventoryLocationCandidate candidate = new InventoryLocationCandidate(dto.getSide(), dto.getRowNumber(), dto.getLayer());
        Inventory inventory = buildInventory(dto, product, warehouse, assay, isPieces, piecesNum, candidate);
        try {
            inventoryMapper.insert(inventory);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("目标位置已被占用，请刷新后重试");
        }
        warehouseMapper.updateCurCapacity(warehouse.getId(), warehouse.getCurCapacity() + 1);
        if (Boolean.TRUE.equals(product.getCanStack()) && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2) {
            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
        }
        InVO inVO = InVO.createDefault();
        inVO.setRemainingQuantity(0);
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

    private List<InventoryLocationCandidate> findAvailableLocationCandidates(Integer warehouseId, String preferredSide,
                                                                             int maxRows, boolean canStack) {
        List<InventoryLocationCandidate> candidates = new ArrayList<>();
        List<String> sides = preferredSides(preferredSide);
        addAvailableLocationCandidates(candidates, warehouseId, sides, maxRows, 1);
        if (canStack) {
            addAvailableLocationCandidates(candidates, warehouseId, sides, maxRows, 2);
        }
        return candidates;
    }

    private void addAvailableLocationCandidates(List<InventoryLocationCandidate> candidates, Integer warehouseId,
                                                List<String> sides, int maxRows, int layer) {
        for (String side : sides) {
            List<Integer> usedRows = inventoryMapper.getUsedRowListForUpdate(warehouseId, side, layer);
            for (Integer rowNumber : getUnusedRowNumbers(usedRows, maxRows)) {
                candidates.add(new InventoryLocationCandidate(side, rowNumber, layer));
            }
        }
    }

    private List<String> preferredSides(String preferredSide) {
        if ("右".equals(preferredSide)) {
            return List.of("右", "左");
        }
        return List.of("左", "右");
    }

    private int convertLoosePiecesRemaining(InVO inVO, int requestedPieces) {
        return inVO.getRemainingQuantity() != null && inVO.getRemainingQuantity() > 0 ? requestedPieces : 0;
    }

    private BigDecimal calculateTotalWeight(Product product, int quantity, String unit) {
        if (quantity <= 0) {
            return BigDecimal.ZERO;
        }
        if ("1".equals(unit)) {
            return product.getWeightPerPiece().multiply(BigDecimal.valueOf(quantity));
        }
        return product.getWeightPerPiece()
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(BigDecimal.valueOf(product.getPiecesPerPallet()));
    }

    private Inventory buildInventory(BaseInStockDTO dto, Product product, Warehouse warehouse, Assay assay,
                                     Boolean isPieces, Integer piecesNum, InventoryLocationCandidate candidate) {
        Inventory inventory = new Inventory();
        inventory.setWarehouseId(warehouse.getId());
        inventory.setProductId(dto.getProductId());
        inventory.setSide(candidate.side());
        inventory.setRowNumber(candidate.rowNumber());
        inventory.setLayer(candidate.layer());
        inventory.setQuantity(1);
        inventory.setScreenMeshId(product.getScreenMeshId());
        inventory.setEntryDate(dto.getEntryDate());
        inventory.setAssayId(assay == null ? null : assay.getId());
        inventory.setProductStatus(product.getStatus());
        inventory.setCreatedAt(LocalDateTime.now());
        inventory.setInStockId(dto.getInStockId());
        inventory.setPalletCodeId(dto.getPalletCodeId());
        inventory.setPieces(Boolean.TRUE.equals(isPieces) ? piecesNum : 0);
        return inventory;
    }

    private static final class InventoryLocationCandidate {
        private final String side;
        private final Integer rowNumber;
        private final Integer layer;

        private InventoryLocationCandidate(String side, Integer rowNumber, Integer layer) {
            this.side = side;
            this.rowNumber = rowNumber;
            this.layer = layer;
        }

        private String side() {
            return side;
        }

        private Integer rowNumber() {
            return rowNumber;
        }

        private Integer layer() {
            return layer;
        }
    }

    @Override
    @Transactional
    public InVO stackModeInStock(AddSemiProductRecordDTO dto, String operator) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);

        Assay assay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getEntryDate());

        InVO vo = new InVO();
        Integer quantity = dto.getQuantity();
        if (dto.getUnit().equals("0")) {
            Integer remaining = this.stackModeInStockQuantity(dto, warehouse, assay, product, false, 0);
            vo.setRemainingQuantity(remaining);
            vo.setMessage(remaining > 0 ? "库位已满，剩余 " + remaining + " 板未入库" : "入库成功");
        } else {
            Integer modeInStockPiece = this.stackModeInStockPiece(dto, warehouse, assay, product);
            vo.setRemainingQuantity(modeInStockPiece);
            vo.setMessage(modeInStockPiece > 0 ? "库位已满，剩余 " + modeInStockPiece + " 件未入库" : "入库成功");
        }
        int actualQuantity = Math.max(quantity - (vo.getRemainingQuantity() == null ? 0 : vo.getRemainingQuantity()), 0);
        SemiProductRecord record = new SemiProductRecord();
        record.setWarehouseId(warehouse.getId());
        record.setProductId(dto.getProductId());
        record.setQuantity(actualQuantity);
        record.setScreenMeshId(product.getScreenMeshId());
        record.setOperator(operator);
        record.setOperationDate(dto.getEntryDate());
        record.setAssayId(assay == null ? null : assay.getId());
        record.setTotalWeight(calculateTotalWeight(product, actualQuantity, dto.getUnit()));
        record.setCreatedAt(LocalDateTime.now());
        record.setUnit(dto.getUnit());
        recordMapper.insert(record);
        recordSemiInboundEvent(record, product, warehouse, dto, actualQuantity);
        return vo;
    }

    private void recordSemiInboundEvent(SemiProductRecord record, Product product, Warehouse warehouse,
                                        BaseInStockDTO dto, int actualQuantity) {
        if (actualQuantity <= 0) {
            return;
        }
        boolean loosePieces = "1".equals(dto.getUnit());
        int totalPieces = loosePieces
                ? actualQuantity
                : actualQuantity * product.getPiecesPerPallet();
        stockMovementEventService.record(StockMovementEventCommand.builder()
                .eventType("INBOUND")
                .sourceType("SEMI_PRODUCT_RECORD")
                .sourceRecordId(record.getId().longValue())
                .occurredAt(record.getCreatedAt())
                .productId(product.getId())
                .productStatus(product.getStatus())
                .productionDate(dto.getEntryDate())
                .toWarehouseId(warehouse.getId())
                .palletCodeId(dto.getPalletCodeId())
                .boardQuantity(loosePieces ? 0 : actualQuantity)
                .loosePieceQuantity(loosePieces ? actualQuantity : 0)
                .totalPieces(totalPieces)
                .totalWeightKg(record.getTotalWeight())
                .actionKind("INBOUND")
                .build());
    }

    private Integer stackModeInStockPiece(AddSemiProductRecordDTO dto, Warehouse warehouse, Assay assay, Product product) {
        int quantity = dto.getQuantity() == null ? 0 : dto.getQuantity();
        Integer piecesPerPallet = product.getPiecesPerPallet();
        PalletInventoryOccupancyRule.PieceSplit split =
                PalletInventoryOccupancyRule.splitPieces(quantity, piecesPerPallet, "散件入库");
        if (split.fullPalletCount() > 0) {
            dto.setQuantity(split.fullPalletCount());
            Integer remainingQuantity = this.stackModeInStockQuantity(dto, warehouse, assay, product, false, 0);
            if (remainingQuantity > 0) {
                return remainingQuantity * piecesPerPallet + split.loosePieces();
            }
            warehouse = warehouseMapper.selectById(warehouse.getId());
        }
        if (split.loosePieces() > 0) {
            dto.setQuantity(1);
            Integer modeInStockQuantity = this.stackModeInStockQuantity(dto, warehouse, assay, product, true, split.loosePieces());
            if (modeInStockQuantity > 0) {
                return split.loosePieces();
            }
            return 0;
        }
        return 0;
    }

    private Integer stackModeInStockQuantity(AddSemiProductRecordDTO dto, Warehouse warehouse,
                                             Assay assay, Product product, Boolean isPieces, Integer piecesNum) {
        int remaining = dto.getQuantity();
        int insertedCount = 0;
        int conflictCount = 0;
        String preferredSide = dto.getSide();

        while (remaining > 0) {
            warehouse = warehouseMapper.selectById(warehouse.getId());
            List<InventoryLocationCandidate> candidates = findAvailableLocationCandidates(
                    warehouse.getId(),
                    preferredSide,
                    warehouse.getMaxRows(),
                    false
            );
            if (candidates.isEmpty()) {
                break;
            }

            boolean inserted = false;
            for (InventoryLocationCandidate candidate : candidates) {
                Inventory inv = buildInventory(dto, product, warehouse, assay, isPieces, piecesNum, candidate);
                try {
                    inventoryMapper.insert(inv);
                    insertedCount++;
                    remaining--;
                    preferredSide = candidate.side();
                    inserted = true;
                    break;
                } catch (DuplicateKeyException e) {
                    conflictCount++;
                    if (conflictCount >= MAX_LOCATION_RETRY) {
                        throw new BusinessException("库位分配冲突，请重试");
                    }
                    break;
                }
            }
            if (!inserted && candidates.isEmpty()) {
                break;
            }
        }

        warehouseMapper.updateCurCapacity(warehouse.getId(), warehouse.getCurCapacity() + insertedCount);
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
