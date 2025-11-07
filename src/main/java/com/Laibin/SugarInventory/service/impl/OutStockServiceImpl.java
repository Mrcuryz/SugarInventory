package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.*;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.InStockService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.OutStockService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutStockServiceImpl implements OutStockService, LoggableService<OutStock> {

    private final InventoryMapper inventoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final OutStockMapper outStockMapper;
    private final ProductMapper productMapper;
    private final AssayMapper assayMapper;
    private final UserMapper userMapper;

    @Lazy
    @Resource
    private InStockService inStockService;

    @Lazy
    @Resource
    private SemiProductRecordService semiProductRecordService;

    @Transactional
    @Override
    public OutVO processOutStock(OutStockRequestDTO dto, Integer operatorId) {
        this.outStock(dto, operatorId);
        // **7. 返回出库结果**
        OutVO outVO = new OutVO();
        outVO.setRemainingQuantity(0);
        outVO.setMessage("出库成功！");
        return outVO;
    }

    private List<BatchInfo> outStock(OutStockRequestDTO dto, Integer operatorId) {
        // 1. 获取库存记录（按先进后出排序）
        int remainingQuantity = dto.getQuantity(); // 需出库的总数量
        String currentSide = dto.getSide(); // 当前出库侧

        Inventory curInventory = inventoryMapper.getLast(dto.getWarehouseId());
        if (curInventory == null) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }
        int curLayer = curInventory.getLayer(); // 当前堆积层数
        int curCapacity;
        List<Inventory> inventoryList = new ArrayList<>();
        String nextSide; // 下一个出库侧
        if (currentSide.equals("左"))
            nextSide = "右";
        else
            nextSide = "左";
        // **1. 可堆积产品：先查找第二层**
        if (curLayer == 2 && remainingQuantity > 0) {
            inventoryList.addAll(inventoryMapper.
                    getInventoryForOutStock(dto.getWarehouseId(), currentSide, 2));
            inventoryList.addAll(inventoryMapper.
                    getInventoryForOutStock(dto.getWarehouseId(), nextSide, 2));
        }

        // **2. 查找第一层**
        inventoryList.addAll(inventoryMapper.
                getInventoryForOutStock(dto.getWarehouseId(), currentSide, 1));
        inventoryList.addAll(inventoryMapper.
                getInventoryForOutStock(dto.getWarehouseId(), nextSide, 1));

        curCapacity = inventoryList.size();

        if (curCapacity == 0) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }
        Product product = productMapper.selectById(dto.getProductId());

        // 出库规则修改
        return this.outStock(inventoryList, product, dto.getWarehouseId(), null,
                dto.getQuantity(), dto.getUnit(),
                operatorId, dto.getOutType(), currentSide);
    }

    @Transactional
    @Override
    public InVO transferOut(TransferOutStockRequestDTO request, Integer id) {
        InVO ret = new InVO();
        ret.setRemainingQuantity(0);
        ret.setMessage("入库成功！");
        List<BatchInfo> outStocks = this.outStock(request, id);
        Integer productId = request.getProductId();
        Product product = productMapper.selectById(productId);
        if (CollectionUtils.isEmpty(outStocks)) {
            // **7. 返回出库结果**
            ret.setRemainingQuantity(0);
            ret.setMessage("出库失败！");
            return ret;
        }
        for (BatchInfo outStock : outStocks) {
            String status = product.getStatus();
            if (status.equals("成品")) {
                if (outStock.getQuantity() > 0) {
                    InStockRequestDTO inStockRequestDTO = new InStockRequestDTO();
                    inStockRequestDTO.setProductId(request.getProductId());
                    inStockRequestDTO.setEntryDate(outStock.getEntryDate());
                    inStockRequestDTO.setQuantity(outStock.getQuantity());
                    inStockRequestDTO.setWarehouseName(request.getInWarehouseName());
                    inStockRequestDTO.setUnit("0");
                    inStockRequestDTO.setReturnInStockFlag("1");
                    inStockRequestDTO.setScreenMeshId(outStock.getScreenMeshId());
                    inStockRequestDTO.setSide(request.getSide());
                    inStockRequestDTO.setSemiRecords(new ArrayList<>());
                    InVO inVO = inStockService.stockIn(inStockRequestDTO, id);
                    if (inVO.getRemainingQuantity() > 0) {
                        return inVO;
                    }
                }
                if (outStock.getPieces() > 0) {
                    InStockRequestDTO inStockRequestDTO = new InStockRequestDTO();
                    inStockRequestDTO.setProductId(request.getProductId());
                    inStockRequestDTO.setEntryDate(outStock.getEntryDate());
                    inStockRequestDTO.setQuantity(outStock.getPieces());
                    inStockRequestDTO.setWarehouseName(request.getInWarehouseName());
                    inStockRequestDTO.setUnit("1");
                    inStockRequestDTO.setReturnInStockFlag("1");
                    inStockRequestDTO.setScreenMeshId(outStock.getScreenMeshId());
                    inStockRequestDTO.setSide(request.getSide());
                    inStockRequestDTO.setSemiRecords(new ArrayList<>());
                    InVO inVO = inStockService.stockIn(inStockRequestDTO, id);
                    if (inVO.getRemainingQuantity() > 0) {
                        return inVO;
                    }
                }
            } else {
                if (outStock.getQuantity() > 0) {
                    AddSemiProductRecordDTO inStockRequestDTO = new AddSemiProductRecordDTO();
                    inStockRequestDTO.setProductId(request.getProductId());
                    inStockRequestDTO.setEntryDate(outStock.getEntryDate());
                    inStockRequestDTO.setQuantity(outStock.getQuantity());
                    inStockRequestDTO.setWarehouseName(request.getInWarehouseName());
                    inStockRequestDTO.setUnit("0");
                    inStockRequestDTO.setReturnInStockFlag("1");
                    inStockRequestDTO.setScreenMeshId(outStock.getScreenMeshId());
                    inStockRequestDTO.setSide(request.getSide());
                    InVO inVO = semiProductRecordService.addSemiProductRecord(inStockRequestDTO, id.toString());
                    if (inVO.getRemainingQuantity() > 0) {
                        return inVO;
                    }
                }
                if (outStock.getPieces() > 0) {
                    AddSemiProductRecordDTO inStockRequestDTO = new AddSemiProductRecordDTO();
                    inStockRequestDTO.setProductId(request.getProductId());
                    inStockRequestDTO.setEntryDate(outStock.getEntryDate());
                    inStockRequestDTO.setQuantity(outStock.getPieces());
                    inStockRequestDTO.setWarehouseName(request.getInWarehouseName());
                    inStockRequestDTO.setUnit("1");
                    inStockRequestDTO.setReturnInStockFlag("1");
                    inStockRequestDTO.setScreenMeshId(outStock.getScreenMeshId());
                    inStockRequestDTO.setSide(request.getSide());
                    InVO inVO = semiProductRecordService.addSemiProductRecord(inStockRequestDTO, id.toString());
                    if (inVO.getRemainingQuantity() > 0) {
                        return inVO;
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public void outStock(Product product, Integer warehouseId, LocalDate entryDate,
                         Integer quantity, String unit,
                         Integer operatorId,
                         Integer outType) {
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getProductId, product.getId());
        if (warehouseId != null) {
            queryWrapper.eq(Inventory::getWarehouseId, warehouseId);
        }
        List<Inventory> inventoryList = inventoryMapper.selectList(queryWrapper);
        this.outStock(inventoryList, product, warehouseId, entryDate, quantity, unit, operatorId, outType, null);
    }

    /**
     * 批量出库
     *
     * @param product     产品
     * @param warehouseId 仓库id
     * @param entryDate   入库时间
     * @param quantity    数量
     * @param unit        单位
     * @param operatorId  操作人id
     * @param outType     出库类型:0整板优先，1散件优先
     * @param currentSide 当前出库方向：左 右
     */
    private List<BatchInfo> outStock(List<Inventory> inventoryList, Product product, Integer warehouseId, LocalDate entryDate,
                                     Integer quantity, String unit,
                                     Integer operatorId,
                                     Integer outType, String currentSide) {
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (inventoryList.isEmpty()) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND.getCode(), product.getProductName() + "库存信息未找到");
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        // 处理整版优先和散件优先
        List<Inventory> inventories = this.sortInventory(inventoryList, outType, currentSide);

        inventories = inventories.stream().filter(s -> s.getProductId().equals(product.getId())).collect(Collectors.toList());
        // **3. 开始逐个出库，并按批次统计出库数量**
        Map<String, BatchInfo> batchMap = new HashMap<>();
        // 总出库行
        int totalOutQuantity = 0;
        // 需要出库的散件
        int totalOutPieces = quantity;
        // 如果是出库散件
        boolean isPiecesOut = unit.equals("1");
        if (!isPiecesOut) {
            totalOutPieces = quantity * product.getPiecesPerPallet();
        }
        for (Inventory inventory : inventories) {
            if (totalOutPieces <= 0) break;
            // 创建批次键：产品ID + 生产日期
            String batchKey = inventory.getProductId() + "_" + inventory.getEntryDate();
            // 更新批次信息
            BatchInfo batchInfo = batchMap.getOrDefault(batchKey, new BatchInfo(
                    inventory.getProductId(),
                    inventory.getEntryDate(),
                    inventory.getScreenMeshId()
            ));
            int inventoryTotalPieces = inventory.getPieces() > 0 ? inventory.getPieces() : inventory.getQuantity() * product.getPiecesPerPallet();
            // 出库数量小于该层的数量，扣减该层数量，肯定是出库散数
            if (inventoryTotalPieces > totalOutPieces) {
                batchInfo.incrementPieces(totalOutPieces);
                inventory.setPieces(inventoryTotalPieces - totalOutPieces);
                totalOutPieces = 0;
                inventoryMapper.updatePieces(inventory.getId(), inventory.getPieces());
            } else {
                // 出库数量大于等于该层数量，代表该层数据出完了，直接删除该层记录
                // 如果该层为整板，记录出库整板，如果是散件，记录出库散件数
                if (inventory.getPieces() > 0) {
                    batchInfo.incrementPieces(inventoryTotalPieces);
                } else {
                    batchInfo.incrementQuantity();
                }
                // **直接删除该板**
                inventoryMapper.deleteInventoryById(inventory.getId());
                totalOutPieces = totalOutPieces - inventoryTotalPieces;
                totalOutQuantity++;
            }
            batchMap.put(batchKey, batchInfo);
        }
        // 如果出库数量不足，返回错误**
        if (totalOutPieces > 0) {
            String message = entryDate == null ? product.getProductName() : (product.getProductName() + " 生成日期：" + entryDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            if (isPiecesOut) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), message + "库存不足,还差:" + totalOutPieces + "件");
            } else {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(),
                        message + "库存不足,还差:" + totalOutPieces / product.getPiecesPerPallet() + "板" + " " + totalOutPieces % product.getPiecesPerPallet() + "件");
            }

        }
        // **4. 为每个批次创建出库记录**
        List<OutStock> outStockList = new ArrayList<>();
        LocalDate outDate = LocalDate.now();
        LocalDateTime createdAt = LocalDateTime.now();

        for (BatchInfo batchInfo : batchMap.values()) {
            // 获取化验数据
            Assay assay = assayMapper.selectByProductIdAndDate(batchInfo.getProductId(), batchInfo.getEntryDate());
            if (assay == null) {
                throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);
            }
            OutStock outStock = new OutStock();
            outStock.setWarehouseId(warehouseId);
            outStock.setProductId(batchInfo.getProductId());
            outStock.setOperatorId(operatorId);
            outStock.setInDate(batchInfo.getEntryDate()); // 该批次的入库日期
            outStock.setOutDate(outDate);
            outStock.setCreatedAt(createdAt);
            outStock.setAssayId(assay.getId());
            outStock.setPieces(batchInfo.getPieces());
            outStock.setQuantity(batchInfo.getQuantity());
            outStock.setUnit(unit);
            if (isPiecesOut) {
                outStock.setTotalWeight(product.getWeightPerPiece().multiply(new BigDecimal(batchInfo.getPieces())));
            } else {
                outStock.setTotalWeight(product.getWeightPerPiece()
                        .multiply(new BigDecimal(batchInfo.getQuantity()))
                        .multiply(new BigDecimal(product.getPiecesPerPallet())));
            }

            outStock.setOutType(outType == null ? 0 : outType);
            outStockList.add(outStock);
        }
        // 批量插入出库记录
        if (!outStockList.isEmpty()) {
            for (OutStock outStock : outStockList) {
                outStockMapper.insert(outStock);
            }
        }
        // **6. 同步更新库位信息**
        warehouseMapper.updateCurCapacity(warehouseId, warehouse.getCurCapacity() - totalOutQuantity);
        return new ArrayList<>(batchMap.values());
    }

    /**
     * 排序整版优先和散件优先
     * 0 整版优先
     * 1 散件优先
     */
    private List<Inventory> sortInventory(List<Inventory> inventoryList, Integer outType, String currentSide) {
        // 整版数据
        List<Inventory> inventories = new ArrayList<>(inventoryList.stream()
                .filter(s -> s.getPieces() == 0)
                .sorted(Comparator.comparingInt(Inventory::getLayer)
                        .reversed()).toList());
        // 散件数据
        inventoryList.removeAll(inventories);
        // 判断左右
        if (currentSide != null) {
            if (currentSide.equals("左")) {
                inventories.sort(Comparator.comparingInt(Inventory::getLayer).reversed()
                        .thenComparing(Inventory::getSide).reversed());
                inventoryList.sort(
                        Comparator.comparingInt(Inventory::getPieces).reversed()
                                .thenComparingInt(Inventory::getLayer).reversed()
                                .thenComparing(Inventory::getSide).reversed());
            } else {

                inventories.sort(Comparator.comparingInt(Inventory::getLayer).reversed()
                        .thenComparing(Inventory::getSide));

                inventoryList.sort(
                        Comparator.comparingInt(Inventory::getPieces).reversed()
                                .thenComparingInt(Inventory::getLayer).reversed()
                                .thenComparing(Inventory::getSide));
            }
        }
        // 整版优先
        if (outType == 0) {
            inventories.addAll(inventoryList);
            return inventories;
        } else {
            inventoryList.addAll(inventories);
            return inventoryList;
        }
    }

    @Transactional
    @Override
    public OutVO processStackOutStock(OutStockRequestDTO dto, Integer operatorId) {
        Integer quantity = dto.getQuantity();
        Integer outType = dto.getOutType();
        Product product = productMapper.selectById(dto.getProductId());

        Integer warehouseId = dto.getWarehouseId();
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);

        List<Inventory> inventoryList = inventoryMapper.getInventoryStackOrder(warehouseId);
        if (inventoryList.isEmpty()) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);

        Map<String, BatchInfo> batchMap = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate outDate = LocalDate.now();
        // 总出库行
        int totalOutQuantity = 0;
        // 需要出库的散件
        int totalOutPieces = dto.getQuantity();
        // 如果是出库散件
        boolean isPiecesOut = dto.getUnit().equals("1");
        if (!isPiecesOut) {
            totalOutPieces = quantity * product.getPiecesPerPallet();
        }
        for (Inventory inv : inventoryList) {
            if (totalOutPieces <= 0) break;
            String batchKey = inv.getProductId() + "_" + inv.getEntryDate();

            BatchInfo batchInfo = batchMap.getOrDefault(batchKey, new BatchInfo(
                    inv.getProductId(),
                    inv.getEntryDate(),
                    inv.getScreenMeshId()
            ));
            int inventoryTotalPieces = inv.getPieces() > 0 ? inv.getPieces() : inv.getQuantity() * product.getPiecesPerPallet();
            // 出库数量小于该层的数量，扣减该层数量，肯定是出库散数
            if (inventoryTotalPieces > totalOutPieces) {
                batchInfo.incrementPieces(totalOutPieces);
                inv.setPieces(inventoryTotalPieces - totalOutPieces);
                totalOutPieces = 0;
                inventoryMapper.updatePieces(inv.getId(), inv.getPieces());
            } else {
                // 出库数量大于等于该层数量，代表该层数据出完了，直接删除该层记录
                // 如果该层为整板，记录出库整板，如果是散件，记录出库散件数
                if (inv.getPieces() > 0) {
                    batchInfo.incrementPieces(inventoryTotalPieces);
                } else {
                    batchInfo.incrementQuantity();
                }
                // **直接删除该板**
                inventoryMapper.deleteInventoryById(inv.getId());
                totalOutPieces = totalOutPieces - inventoryTotalPieces;
                totalOutQuantity++;
            }
            batchMap.put(batchKey, batchInfo);
        }
        // 如果出库数量不足，返回错误**
        if (totalOutPieces > 0) {
            String message = product.getProductName();
            if (isPiecesOut) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), message + "库存不足,还差:" + totalOutPieces + "件");
            } else {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(),
                        message + "库存不足,还差:" + totalOutPieces / product.getPiecesPerPallet() + "板" + " " + totalOutPieces % product.getPiecesPerPallet() + "件");
            }
        }

        // 批量插入出库记录
        for (BatchInfo batchInfo : batchMap.values()) {
            Assay assay = assayMapper.selectByProductIdAndDate(batchInfo.getProductId(), batchInfo.getEntryDate());
            if (assay == null) throw new BusinessException(ErrorCode.ASSAY_NOT_FOUND);
            OutStock outStock = new OutStock();
            outStock.setWarehouseId(warehouseId);
            outStock.setProductId(batchInfo.getProductId());
            outStock.setOperatorId(operatorId);
            outStock.setInDate(batchInfo.getEntryDate()); // 该批次的入库日期
            outStock.setOutDate(outDate);
            outStock.setCreatedAt(now);
            outStock.setAssayId(assay.getId());
            outStock.setPieces(batchInfo.getPieces());
            outStock.setQuantity(batchInfo.getQuantity());
            outStock.setUnit(dto.getUnit());
            if (isPiecesOut) {
                outStock.setTotalWeight(product.getWeightPerPiece().multiply(new BigDecimal(batchInfo.getPieces())));
            } else {
                outStock.setTotalWeight(product.getWeightPerPiece()
                        .multiply(new BigDecimal(batchInfo.getQuantity()))
                        .multiply(new BigDecimal(product.getPiecesPerPallet())));
            }
            outStock.setOutType(outType == null ? 0 : outType);
            outStockMapper.insert(outStock);
        }
        warehouseMapper.updateCurCapacity(warehouseId,
                warehouse.getCurCapacity() - totalOutQuantity);

        // 处理最大容量字段自动修正（如果历史有异常）
        if (warehouse.getMaxRows() * 2 != warehouse.getMaxCapacity()) {
            warehouseMapper.updateMaxCapacity(warehouseId, warehouse.getMaxRows() * 2);
        }

        OutVO vo = new OutVO();
        vo.setRemainingQuantity(0);
        vo.setMessage("出库成功！");
        return vo;
    }


    @Override
    public PageResult<OutStockRecordVO> searchOutRecords(OutRecordQueryDTO query, User user) {
        int offset = (query.getPage() - 1) * query.getSize();

        boolean isStaff = user.getRoleCode().equals("STAFF");

        List<OutStockRecordVO> recordVOList = outStockMapper
                .selectOutStockRecordsByQuery(query, offset, query.getSize(), isStaff, user.getName());

        for (OutStockRecordVO record : recordVOList) {
            Integer id = record.getTestedBy();
            if (id == null) continue;
            String testerName = userMapper.selectById(id).getName();
            record.setTesterName(testerName);

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

        Long total = outStockMapper.countOutStockRecordsByQuery(query, isStaff, user.getName());

        return new PageResult<>(total, recordVOList);

    }

    @Override
    public OutStock findById(Integer id) {
        return outStockMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "out_stock";
    }

    @Data
    private static class BatchInfo {
        private Integer productId;
        private LocalDate entryDate;
        private Integer screenMeshId;
        private int quantity = 0;
        private int pieces = 0;

        public BatchInfo(Integer productId, LocalDate entryDate, Integer screenMeshId) {
            this.productId = productId;
            this.entryDate = entryDate;
            this.screenMeshId = screenMeshId;
        }

        public BatchInfo(Integer productId, LocalDate entryDate, int quantity) {
            this.productId = productId;
            this.entryDate = entryDate;
            this.quantity = quantity;
        }

        public void incrementQuantity() {
            this.quantity++;
        }

        public void incrementQuantity(Integer quantity) {
            this.quantity += quantity;
        }

        public void incrementPieces(Integer pieces) {
            this.pieces += pieces;
        }
    }
}
