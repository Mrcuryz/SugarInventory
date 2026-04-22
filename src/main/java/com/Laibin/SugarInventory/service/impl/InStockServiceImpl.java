package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.*;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.*;
import com.Laibin.SugarInventory.service.model.AssayJudgeOutcome;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author Mrcury
 * @since 2025-02-19
 */
@Service
public class InStockServiceImpl extends ServiceImpl<InStockMapper, InStock> implements InStockService, LoggableService<InStock> {
    @Autowired
    private InStockMapper inStockMapper;
    @Autowired
    private SemiProductRecordMapper semiProductRecordMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private AssayMapper assayMapper;
    @Lazy
    @Autowired
    private OutStockService outStockService;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SemiProductRecordService semiProductRecordService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private InventorySummaryMapper inventorySummaryMapper;
    @Autowired
    private PalletCodeMapper palletCodeMapper;
    @Autowired
    private SemiPreparePoolMapper semiPreparePoolMapper;

    @Autowired
    private AssayStandardJudgeService assayStandardJudgeService;

    private final Integer page = 1;
    private final Integer size = 10;

    @Transactional
    @Override
    public InVO stockIn(InStockRequestDTO dto, Integer operatorId) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 鑾峰彇搴撲綅淇℃伅
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        // 楠岃瘉鍗婃垚鍝佽褰曚腑 useAssay 涓?true 鐨勮褰曟暟閲?
        List<SemiRecordDTO> semiRecords = dto.getSemiRecords();
        long useAssayCount = semiRecords.stream()
                .filter(SemiRecordDTO::getUseAssay)
                .count();

        if (useAssayCount > 1) {
            throw new BusinessException(ErrorCode.MULTIPLE_USE_ASSAY_FLAGS);
        }
        // 鍒ゆ柇搴撳瓨鏄惁鍏呰冻, 骞朵笖鎵ｅ噺鍗婃垚鍝佹暟閲?
        //returnInStockFlag绛変簬1鏃朵负閫€璐у叆搴擄紝涓嶉獙璇佸拰鎵ｅ噺搴撳瓨
        if (!dto.getReturnInStockFlag().equals("1")) {
            this.judgeInventory(semiRecords, operatorId);
            // 杩欓噷瑕佸啀鎷夸竴娆″簱浣嶄俊鎭紝鎵ｅ噺搴撳瓨鍚庝粨搴撳閲忔洿鏂?
            warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        }
        Assay assay = new Assay();
        Assay semiAssay;

        if (useAssayCount == 1) {
            // 鑾峰彇鏍囪涓?useAssay 鐨勫崐鎴愬搧璁板綍
            SemiRecordDTO selectedSemi = semiRecords.stream()
                    .filter(SemiRecordDTO::getUseAssay)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SEMI_RECORD));
            // 鏍规嵁鍗婃垚鍝両D鍜岀敓浜ф棩鏈熸煡璇㈠寲楠岃褰?
            semiAssay = getAssayByProductIdAndDate(selectedSemi.getSemiProductId(), selectedSemi.getProductionDate());
            AssaySubmitDTO dtoAssay = new AssaySubmitDTO();
            dtoAssay.setProductId(dto.getProductId());
            dtoAssay.setSampleDate(dto.getEntryDate());
            dtoAssay.setColorValue(semiAssay.getColorValue());
            dtoAssay.setReducingSugar(semiAssay.getReducingSugar());
            dtoAssay.setDryWeight(semiAssay.getDryWeight());
            dtoAssay.setInsolubleImpurity(semiAssay.getInsolubleImpurity());
            dtoAssay.setPhValue(semiAssay.getPhValue());
            dtoAssay.setSucrose(semiAssay.getSucrose());
            dtoAssay.setConductivityAsh(semiAssay.getConductivityAsh());

            AssayJudgeOutcome judgeOutcome = assayStandardJudgeService.judge(product, dtoAssay);
            BeanUtils.copyProperties(dtoAssay, assay);
            assay.setTestedBy(operatorId);
            assay.setQualifiedStandards(judgeOutcome.getQualifiedStandardsJson());
            assay.setIsQualified(judgeOutcome.getCompatibleConclusion());
            assay.setAppliedStandardId(judgeOutcome.getAppliedStandardId());
            assay.setAppliedStandardName(judgeOutcome.getAppliedStandardName());
            assay.setAppliedStandardVersion(judgeOutcome.getAppliedStandardVersion());
            assay.setJudgeResult(judgeOutcome.getJudgeResult());
            assay.setJudgeMessage(judgeOutcome.getJudgeMessage());
            assay.setFailedMetricCount(judgeOutcome.getFailedMetricCount());
            assay.setFailedMetricsJson(judgeOutcome.getFailedMetricsJson());
            assay.setStandardSnapshotJson(judgeOutcome.getStandardSnapshotJson());

            Assay todaysAssay = getAssayByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
            if (todaysAssay != null) {
                int version = todaysAssay.getVersion() + 1;
                assay.setVersion(version);
            } else {
                assay.setVersion(1);
            }
            assay.setCreatedAt(LocalDateTime.now());

            assayMapper.insert(assay);
            assay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        } else {
            // 鏍规嵁鎴愬搧ID鍜屽叆搴撴棩鏈熸煡璇㈠寲楠岃褰?
            assay = getAssayByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        }

        InStock inStock = new InStock();
        // 3. 瑙ｆ瀽鍓嶇浼犳潵鐨勫崐鎴愬搧 JSON锛屽苟鏌ヨ鏁版嵁搴?
        if (!semiRecords.isEmpty()) {
            for (SemiRecordDTO recordDTO : semiRecords) {
                int count = semiProductRecordMapper.existsByProductIdAndDate
                        (recordDTO.getSemiProductId(), recordDTO.getProductionDate());
                if (count == 0)
                    throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
            }
            String semiProductRecordsJson = convertToJson(semiRecords);
            inStock.setSemiProductRecords(semiProductRecordsJson);
        }

        int maxRows = warehouse.getMaxRows();
        int remainingQuantity = dto.getQuantity();
        String currentSide = dto.getSide(); // 榛樿浠庡乏渚у瓨鏀?
        boolean canStack = product.getCanStack(); // 鏄惁鍙爢绉?

        // **3. 棰勮幏鍙栧綋鍓嶅簱浣嶇殑瀛樺偍鎯呭喌**
        int leftUsedRowsLayer1 = inventoryMapper.getUsedRows(warehouse.getId(), "左", 1);
        int rightUsedRowsLayer1 = inventoryMapper.getUsedRows(warehouse.getId(), "右", 1);
        int leftUsedRowsLayer2 = inventoryMapper.getUsedRows(warehouse.getId(), "左", 2);
        int rightUsedRowsLayer2 = inventoryMapper.getUsedRows(warehouse.getId(), "右", 2);

        int currentLayer = (leftUsedRowsLayer2 > 0 || rightUsedRowsLayer2 > 0) ? 2 : 1;

        // 璁＄畻搴撲綅鍓╀綑瀹归噺
        int remainingCapacity = 2 * maxRows - leftUsedRowsLayer1 - rightUsedRowsLayer1;
        if (product.getCanStack() && currentLayer == 1) {
            remainingCapacity += 2 * maxRows;
        }

        if (remainingCapacity <= 0) {
            throw new BusinessException(ErrorCode.WAREHOUSE_FULL);
        }

        int quantity = dto.getQuantity() > remainingCapacity ? remainingCapacity : dto.getQuantity();

        BigDecimal totalWeight = product.getWeightPerPiece()
                .multiply(new BigDecimal(quantity)
                        .multiply(new BigDecimal(product.getPiecesPerPallet())));

        // **4. 璁板綍鍏ュ簱淇℃伅**
        inStock.setWarehouseId(warehouse.getId());
        inStock.setProductId(dto.getProductId());
        inStock.setQuantity(quantity);
        inStock.setCreatedBy(operatorId);
        inStock.setEntryDate(dto.getEntryDate());
        inStock.setAssayId(assay == null ? null : assay.getId());
        inStock.setScreenMeshId(product.getScreenMeshId());
        inStock.setTotalWeight(totalWeight);
        inStock.setCreatedAt(LocalDateTime.now());
        inStock.setUnit(dto.getUnit());
        inStockMapper.insert(inStock);
        Integer inStockId = inStock.getId();
        // 淇濆瓨鍏ュ簱鍗婃垚鍝佹槑缁?
        if (!semiRecords.isEmpty()) {
            inStockMapper.saveInStockItem(semiRecords, inStockId);
        }
        dto.setInStockId(inStockId);
        // 瀛樺叆鏉挎暟
        InVO inVO;
        if (dto.getUnit().equals("0")) {
            // 鏁寸増鍏ュ簱
            inVO = semiProductRecordService.handlerInStock(dto, product, warehouse, assay, false, 0);
        } else {
            // 鎬绘暎浠舵暟
            inVO = semiProductRecordService.handlerInStockPieces(dto, product, warehouse, assay);
        }
//        // **5. 寮€濮嬪瓨鏀?*
//        while (remainingQuantity > 0) {
//            int usedRows = (currentSide.equals("宸?)) ?
//                    (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
//                    : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
//
//            if (usedRows < maxRows) {
//                int rowNumber = usedRows + 1;
//
//                // **瀛樺偍鍗曟澘**
//                Inventory inventory = new Inventory();
//                inventory.setWarehouseId(warehouse.getId());
//                inventory.setProductId(dto.getProductId());
//                inventory.setSide(currentSide);
//                inventory.setRowNumber(rowNumber);
//                inventory.setLayer(currentLayer);
//                inventory.setQuantity(1);
//                inventory.setEntryDate(dto.getEntryDate());
//                inventory.setInStockId(inStockId);
//                inventory.setScreenMeshId(dto.getScreenMeshId());
//                inventory.setAssayId(assay.getId());
//                inventory.setProductStatus(product.getStatus());
//                inventory.setSemiRecordId(null);
//                inventory.setCreatedAt(LocalDateTime.now());
//
//                try {
//                    inventoryMapper.insert(inventory);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
//                }
//                remainingQuantity--;
//
//                // **鏇存柊鏈湴鍙橀噺**
//                if (currentSide.equals("宸?)) {
//                    if (currentLayer == 1) leftUsedRowsLayer1++;
//                    else leftUsedRowsLayer2++;
//                } else {
//                    if (currentLayer == 1) rightUsedRowsLayer1++;
//                    else rightUsedRowsLayer2++;
//                }
//            }
//
//            // **濡傛灉褰撳墠鍒楁弧锛屽皾璇曞垏鎹㈠埌鍙︿竴渚?*
//            if (remainingQuantity > 0 && usedRows >= maxRows) {
//                currentSide = currentSide.equals("宸?) ? "鍙? : "宸?;
//                usedRows = (currentSide.equals("宸?)) ?
//                        (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
//                        : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
//            }
//
//            // **濡傛灉绗竴灞傛弧浜嗭紝妫€鏌ユ槸鍚﹀彲浠ュ爢绉?*
//            if (remainingQuantity > 0 && leftUsedRowsLayer1 >= maxRows && rightUsedRowsLayer1 >= maxRows) {
//                if (canStack && currentLayer == 1) {
//                    // **鍒囨崲鍒扮浜屽眰**
//                    currentLayer = 2;
//                    leftUsedRowsLayer2 = 0;
//                    rightUsedRowsLayer2 = 0;
//                } else if (!canStack || (leftUsedRowsLayer2 >= maxRows && rightUsedRowsLayer2 >= maxRows)) {
//                    // **濡傛灉涓嶅彲鍫嗙Н锛屾垨鑰呯浜屽眰涔熸弧浜嗭紝鍒欐彁绀哄簱浣嶅凡婊?*
//                    warehouseMapper.updateCurCapacity(
//                            warehouse.getId(), warehouse.getCurCapacity() + quantity);
//                    if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
//                        warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
//
//                    InVO inVO = new InVO();
//                    inVO.setRemainingQuantity(remainingQuantity);
//                    inVO.setMessage("搴撲綅宸叉弧锛佸墿浣?" + remainingQuantity + " 鏉夸骇鍝侊紝璇烽€夋嫨鏂板簱浣?);
//                    return inVO;
//                }
//            }
//        }
//
//        // **6. 鍚屾鏇存柊搴撲綅淇℃伅**
//        warehouseMapper.updateCurCapacity(
//                warehouse.getId(), warehouse.getCurCapacity() + quantity);
//        if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
//            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
//
//        InVO inVO = new InVO();
//        inVO.setRemainingQuantity(remainingQuantity);
//        if (inVO.getMessage() == null) {
//            inVO.setMessage("鍏ュ簱鎴愬姛锛?);
//        }
        return inVO;
    }

    /**
     * 鍒ゆ柇搴撳瓨鏄惁婊¤冻鍏ュ簱瑕佹眰
     *
     * @param semiRecords 鍗婃垚鍝佸叆搴撹褰?
     */
    private void judgeInventory(List<SemiRecordDTO> semiRecords, Integer operatorId) {
        List<Product> products = productMapper.selectBatchIds(semiRecords.stream().map(SemiRecordDTO::getSemiProductId).distinct().toList());
        Map<Integer, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, s -> s));
        for (SemiRecordDTO semiRecord : semiRecords) {
            if (Boolean.TRUE.equals(semiRecord.getFromPreparePool())) {
                validatePreparePoolSemiRecord(semiRecord);
                continue;
            }
            Integer warehouseId = semiRecord.getWarehouseId();
            LocalDate productionDate = semiRecord.getProductionDate();
            Product product = productMap.get(semiRecord.getSemiProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "半成品" + semiRecord.getProductName() + "已被删除");
            }
            VInventorySummary inventorySummary = inventorySummaryMapper.selectOne(
                    new LambdaQueryWrapper<VInventorySummary>()
                            .eq(VInventorySummary::getWarehouseId, warehouseId)
                            .eq(VInventorySummary::getProductId, semiRecord.getSemiProductId())
                            .eq(VInventorySummary::getEntryDate, productionDate)
                            .last("limit 1")
            );
            String msg = "半成品" + semiRecord.getProductName() + "，生产日期:" + semiRecord.getProductionDate();
            if (inventorySummary == null) {
                throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND.getCode(), msg + "鐨勫簱瀛樹俊鎭湭鎵惧埌");
            }
            String inventoryMsg = (inventorySummary.getTotalQuantity() > 0 ? inventorySummary.getTotalQuantity() + "板" : "")
                    + (inventorySummary.getTotalPieces() > 0 ? inventorySummary.getTotalPieces() + "件" : "");
            // 0锛屾暣鏉匡紝1鏁ｄ欢
            if (semiRecord.getUnit().equals("0")) {
                if ((inventorySummary.getTotalQuantity() + inventorySummary.getTotalPieces() / product.getPiecesPerPallet()) < semiRecord.getQuantity()) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), msg + "的库存不足，剩余:" + inventoryMsg);
                }
            } else {
                //鏁ｄ欢
                if ((inventorySummary.getTotalQuantity() * product.getPiecesPerPallet() + inventorySummary.getTotalPieces()) < semiRecord.getQuantity()) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), msg + "的库存不足，剩余:" + inventoryMsg);
                }
            }
            // 鎵ｅ噺鍗婃垚鍝佹暟閲?
            outStockService.outStock(product, warehouseId, semiRecord.getProductionDate(), semiRecord.getQuantity(), semiRecord.getUnit(), operatorId, 1);
        }
    }

    private void validatePreparePoolSemiRecord(SemiRecordDTO semiRecord) {
        if (semiRecord.getSemiPalletCodeId() == null) {
            throw new BusinessException("澶囨枡姹犲崐鎴愬搧鏉ユ簮缂哄皯鎵樼洏淇℃伅");
        }
        PalletCode semiPallet = palletCodeMapper.selectById(semiRecord.getSemiPalletCodeId());
        if (semiPallet == null) {
            throw new BusinessException("半成品托盘不存在");
        }
        if (!"INSTOCK".equalsIgnoreCase(semiPallet.getStatus())) {
            throw new BusinessException("半成品托盘当前不在可消耗状态");
        }
        if (!"半成品".equals(semiPallet.getProductStatus())) {
            throw new BusinessException("仅允许使用半成品托盘");
        }
        int cycleNo = semiRecord.getCycleNo() == null ? (semiPallet.getCurrentCycleNo() == null ? 0 : semiPallet.getCurrentCycleNo()) : semiRecord.getCycleNo();
        SemiPreparePool preparePool = semiPreparePoolMapper.selectActiveByPalletAndCycle(semiPallet.getId(), cycleNo);
        if (preparePool == null) {
            throw new BusinessException("半成品托盘未进入备料池，不能用于成品入库");
        }
    }

    private boolean checkStandardCompliance(AssaySubmitDTO assay, QualityStandard standard) {
        return checkValue(assay.getColorValue(), standard.getColorMin(), standard.getColorMax()) &&
                checkValue(assay.getReducingSugar(), standard.getReducingSugarMin(), standard.getReducingSugarMax()) &&
                checkValue(assay.getDryWeight(), standard.getDryWeightMin(), standard.getDryWeightMax()) &&
                checkValue(assay.getConductivityAsh(), standard.getConductivityAshMin(), standard.getConductivityAshMax()) &&
                checkValue(assay.getSucrose(), standard.getSucroseMin(), standard.getSucroseMax()) &&
                checkValue(assay.getInsolubleImpurity(), standard.getInsolubleImpurityMin(), standard.getInsolubleImpurityMax()) &&
                checkValue(assay.getPhValue(), standard.getPhMin(), standard.getPhMax());
    }

    //鏍￠獙鏌愪釜鏁板€兼槸鍚︾鍚堟寚鏍?
    private boolean checkValue(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null && min == null && max == null) {
            return true; // 鍖栭獙鏁版嵁涓虹┖锛屼笖鏍囧噯閲屼笂涓嬮檺閮戒负绌猴紝鍒欐棤闇€鏍￠獙
        } else if (value == null) {
            return false; // 鍖栭獙鏁版嵁涓虹┖锛屽垯涓嶅悎鏍?
        }
        if (min != null && max == null) {
            return value.compareTo(min) >= 0;  // 鍙湁涓嬮檺锛屽繀椤诲ぇ浜庣瓑浜庝笅闄?
        }
        if (min == null && max != null) {
            return value.compareTo(max) <= 0;  // 鍙湁涓婇檺锛屽繀椤诲皬浜庣瓑浜庝笂闄?
        }
        if (min != null && max != null) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0; // 鍚屾椂瀛樺湪涓婁笅闄?
        }
        return true; // 濡傛灉鏍囧噯閲屼笂涓嬮檺閮戒负绌猴紝鍒欓粯璁ゅ悎鏍?
    }

    // 鍏ュ簱鎿嶄綔锛堜娇鐢ㄥ潗鏍囨柟寮忥級
//    @Transactional
//    @Override
//    public void handleStockIn(InStockRequestDTO request, Integer operatorId) {
//        Product product = productMapper.selectById(request.getProductId());
//        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
//
//        // 2. 璁＄畻鎬绘暟閲?
//        Integer totalQuantity = request.getLocations().stream()
//                .map(InStockRequestDTO.LocationDTO::getQuantity)
//                .reduce(0, Integer::sum);
//
//        // 鑾峰彇鍖栭獙璁板綍
//        Assay assay = getAssayByProductIdAndDate(request.getProductId(), LocalDate.now());
//        if (assay == null) {
//            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
//        }
//
//        SemiProductRecord semiProductRecord = semiProductRecordMapper
//                .selectByProductIdAndDate(request.getSemiProductId(), request.getSemiDate());
//        if(semiProductRecord == null){
//            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
//        }
//
//        // 3. 鍒涘缓鍏ュ簱璁板綍
//        InStock inStock = new InStock();
//        inStock.setProductId(request.getProductId());
//        inStock.setWarehouseId(request.getWarehouseId());
//        inStock.setQuantity(totalQuantity);
//        inStock.setWeightPerPiece(product.getWeightPerPiece());
//        inStock.setTotalWeight(product.getWeightPerPiece().multiply(new BigDecimal(totalQuantity)));
//        inStock.setEntryDate(LocalDate.now());
//        inStock.setAssayId(assay.getId());
//        inStock.setSemiProductRecordId(semiProductRecord.getId());
//        inStock.setScreenMeshId(request.getScreenMeshId());
//        inStock.setCreatedAt(LocalDateTime.now());
//        inStock.setCreatedBy(operatorId);
//
//        if(inStockMapper.insert(inStock) < 1){
//            throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
//        }
//
//        Inventory inventory = inventoryMapper.existSameInventory(
//                request.getWarehouseId(), product.getId(), LocalDate.now(), request.getScreenMeshId());
//        // 鏌ヨ鏄惁宸插瓨鍦ㄥ簱瀛樿褰?
//        if(inventory != null) {
//            // 鏇存柊搴撳瓨涓昏褰?
//            inventoryMapper.updateInventory(
//                    null,
//                    inventory.getTotalQuantity() + totalQuantity,
//                    null,
//                    null,
//                    inventory.getInStockId(),
//                    request.getScreenMeshId()
//            );
//        } else {
//            // 鍒涘缓搴撳瓨涓昏褰?
//            inventory = new Inventory();
//            inventory.setProductId(product.getId());
//            inventory.setWarehouseId(request.getWarehouseId());
//            inventory.setEntryDate(LocalDate.now());
//            inventory.setInStockId(inStock.getId());
//            inventory.setScreenMeshId(request.getScreenMeshId());
//            inventory.setAssayId(assay.getId());
//            inventory.setTotalQuantity(totalQuantity);
//            inventory.setCreatedAt(LocalDateTime.now());
//            if (inventoryMapper.insert(inventory) < 1){
//                throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
//            }
//        }
//
//        // 5. 鎻掑叆鎴栨洿鏂板簱瀛樹綅缃褰曪紙鎵归噺鎿嶄綔锛?
//        List<InventoryLocation> locationsToInsert = new ArrayList<>();
//        for (InStockRequestDTO.LocationDTO dto : request.getLocations()) {
//            // 鏌ヨ鏄惁宸插瓨鍦ㄨ浣嶇疆
//            InventoryLocation location = inventoryLocationMapper.selectForUpdate(
//                    inventory.getId(),
//                    dto.getCoordinates().getX(),
//                    dto.getCoordinates().getY()
//            );
//
//            if (location == null) {
//                // 濡傛灉浣嶇疆璁板綍涓嶅瓨鍦紝鏂板璁板綍
//                location = new InventoryLocation();
//                location.setInventoryId(inventory.getId());
//                location.setCoordinateX(dto.getCoordinates().getX());
//                location.setCoordinateY(dto.getCoordinates().getY());
//                location.setQuantity(dto.getQuantity());
//                locationsToInsert.add(location);
//            } else {
//                // 濡傛灉浣嶇疆璁板綍宸插瓨鍦紝鏇存柊鏁伴噺
//                inventoryLocationMapper.AddQuantity(location.getId(), dto.getQuantity());
//            }
//        }
//
//        // 鎵归噺鎻掑叆鏂扮殑搴撳瓨浣嶇疆璁板綍
//        if (!locationsToInsert.isEmpty()) {
//            if (inventoryLocationMapper.batchInsert(locationsToInsert) < 1) {
//                throw new BusinessException(ErrorCode.INSERT_INVENTORY_LOCATION_FAILED);
//            }
//        }
//    }

    @Override
    public PageResult<InStockVO> queryInStockRecords(InStockQueryDTO queryDTO, User currentUser) {
        // 璁＄畻鍒嗛〉鍋忕Щ閲?
        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();

        // 鍒ゆ柇鏄惁涓哄憳宸?
        Boolean isStaff = currentUser.getRoleCode().equals("STAFF");

        // 鑾峰彇鍒嗛〉鏁版嵁
        List<InStockVO> records = inStockMapper.selectInStockList(
                queryDTO,
                currentUser.getId(),
                isStaff,
                offset,
                queryDTO.getSize()
        );

        for (InStockVO record : records) {
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
                record.setTestedBy(null);
                record.setTesterName(null);
                record.setIsQualified(null);
                record.setQualifiedStandards(null);
            }
        }

        System.out.println("records:" + records);

        // 鑾峰彇鎬昏褰曟暟
        Long total = inStockMapper.countInStockRecords(
                queryDTO,
                currentUser.getId(),
                isStaff
        );
        System.out.println("total:" + total);

        return new PageResult<>(total, records);
    }

    private Assay getAssayByProductIdAndDate(Integer productId, LocalDate date) {
        // 鏌ユ壘褰撳ぉ鐨勫寲楠岃褰?
        return assayMapper.selectByProductIdAndDate(productId, date);
    }

    @Override
    public InStock findById(Integer id) {
        return inStockMapper.selectById(id);
    }

    @Override
    public String getTableName() {
        return "in_stock";
    }

    private String convertToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]"; // 鍙戠敓寮傚父鏃讹紝杩斿洖绌?JSON
        }
    }
}

