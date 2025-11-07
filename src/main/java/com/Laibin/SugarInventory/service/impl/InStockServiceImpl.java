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
 * 服务实现类
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
    private QualityStandardMapper qualityStandardMapper;
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

    private final Integer page = 1;
    private final Integer size = 10;

    @Transactional
    @Override
    public InVO stockIn(InStockRequestDTO dto, Integer operatorId) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        // 2. 获取库位信息
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        // 验证半成品记录中 useAssay 为 true 的记录数量
        List<SemiRecordDTO> semiRecords = dto.getSemiRecords();
        long useAssayCount = semiRecords.stream()
                .filter(SemiRecordDTO::getUseAssay)
                .count();

        if (useAssayCount > 1) {
            throw new BusinessException(ErrorCode.MULTIPLE_USE_ASSAY_FLAGS);
        }
        // 判断库存是否充足, 并且扣减半成品数量,
        //returnInStockFlag等于1时为退货入库，不验证和扣减库存
        if (!dto.getReturnInStockFlag().equals("1")) {
            this.judgeInventory(semiRecords, operatorId);
            // 这里要再拿一次库位信息，扣减库存后仓库容量更新
           warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        }
        Assay assay = new Assay();
        Assay semiAssay;

        if (useAssayCount == 1) {
            // 获取标记为 useAssay 的半成品记录
            SemiRecordDTO selectedSemi = semiRecords.stream()
                    .filter(SemiRecordDTO::getUseAssay)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SEMI_RECORD));
            // 根据半成品ID和生产日期查询化验记录
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

            List<QualityStandard> standards = qualityStandardMapper.selectByProductType(product.getProductType());
            if (standards.isEmpty()) {
                throw new BusinessException("未找到该产品的质量标准");
            }
            List<String> qualifiedStandards = new ArrayList<>();
            boolean isQualified = false;

            for (QualityStandard standard : standards) {
                if (checkStandardCompliance(dtoAssay, standard)) {
                    qualifiedStandards.add(standard.getStandardName());
                    isQualified = true;  // 只要有一个标准符合，就算合格
                }
            }
            BeanUtils.copyProperties(dtoAssay, assay);
            assay.setTestedBy(operatorId);
            assay.setSucrose(dtoAssay.getSucrose());
            assay.setIsQualified(isQualified ? "合格" : "不合格");

            Assay todaysAssay = getAssayByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
            if (todaysAssay != null) {
                int version = todaysAssay.getVersion() + 1;
                assay.setVersion(version);
            } else {
                assay.setVersion(1);
            }
            if (!isQualified)
                qualifiedStandards.add("无");
            assay.setCreatedAt(LocalDateTime.now());
            try {
                assay.setQualifiedStandards(objectMapper.writeValueAsString(qualifiedStandards));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            assayMapper.insert(assay);
            assay = assayMapper.selectByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        } else {
            // 根据成品ID和入库日期查询化验记录
            assay = getAssayByProductIdAndDate(dto.getProductId(), dto.getEntryDate());
        }

        if (assay == null) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }

        InStock inStock = new InStock();
        // 3. 解析前端传来的半成品 JSON，并查询数据库
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

        // **4. 记录入库信息**
        inStock.setWarehouseId(warehouse.getId());
        inStock.setProductId(dto.getProductId());
        inStock.setQuantity(quantity);
        inStock.setCreatedBy(operatorId);
        inStock.setEntryDate(dto.getEntryDate());
        inStock.setAssayId(assay.getId());
        inStock.setScreenMeshId(dto.getScreenMeshId());
        inStock.setTotalWeight(totalWeight);
        inStock.setCreatedAt(LocalDateTime.now());
        inStock.setUnit(dto.getUnit());
        inStockMapper.insert(inStock);
        Integer inStockId = inStock.getId();
        // 保存入库半成品明细
        if (!semiRecords.isEmpty()) {
            inStockMapper.saveInStockItem(semiRecords, inStockId);
        }
        dto.setInStockId(inStockId);
        // 存入板数
        InVO inVO;
        if (dto.getUnit().equals("0")) {
            // 整版入库
            inVO = semiProductRecordService.handlerInStock(dto, product, warehouse, assay, false, 0);
        } else {
            // 总散件数
            inVO = semiProductRecordService.handlerInStockPieces(dto, product, warehouse, assay);
        }
//        // **5. 开始存放**
//        while (remainingQuantity > 0) {
//            int usedRows = (currentSide.equals("左")) ?
//                    (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
//                    : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
//
//            if (usedRows < maxRows) {
//                int rowNumber = usedRows + 1;
//
//                // **存储单板**
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
//                // **更新本地变量**
//                if (currentSide.equals("左")) {
//                    if (currentLayer == 1) leftUsedRowsLayer1++;
//                    else leftUsedRowsLayer2++;
//                } else {
//                    if (currentLayer == 1) rightUsedRowsLayer1++;
//                    else rightUsedRowsLayer2++;
//                }
//            }
//
//            // **如果当前列满，尝试切换到另一侧**
//            if (remainingQuantity > 0 && usedRows >= maxRows) {
//                currentSide = currentSide.equals("左") ? "右" : "左";
//                usedRows = (currentSide.equals("左")) ?
//                        (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
//                        : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);
//            }
//
//            // **如果第一层满了，检查是否可以堆积**
//            if (remainingQuantity > 0 && leftUsedRowsLayer1 >= maxRows && rightUsedRowsLayer1 >= maxRows) {
//                if (canStack && currentLayer == 1) {
//                    // **切换到第二层**
//                    currentLayer = 2;
//                    leftUsedRowsLayer2 = 0;
//                    rightUsedRowsLayer2 = 0;
//                } else if (!canStack || (leftUsedRowsLayer2 >= maxRows && rightUsedRowsLayer2 >= maxRows)) {
//                    // **如果不可堆积，或者第二层也满了，则提示库位已满**
//                    warehouseMapper.updateCurCapacity(
//                            warehouse.getId(), warehouse.getCurCapacity() + quantity);
//                    if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
//                        warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
//
//                    InVO inVO = new InVO();
//                    inVO.setRemainingQuantity(remainingQuantity);
//                    inVO.setMessage("库位已满！剩余 " + remainingQuantity + " 板产品，请选择新库位");
//                    return inVO;
//                }
//            }
//        }
//
//        // **6. 同步更新库位信息**
//        warehouseMapper.updateCurCapacity(
//                warehouse.getId(), warehouse.getCurCapacity() + quantity);
//        if (product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
//            warehouseMapper.updateMaxCapacity(warehouse.getId(), warehouse.getMaxRows() * 2 * 2);
//
//        InVO inVO = new InVO();
//        inVO.setRemainingQuantity(remainingQuantity);
//        if (inVO.getMessage() == null) {
//            inVO.setMessage("入库成功！");
//        }
        return inVO;
    }

    /**
     * 判断库存是否满足入库要求
     *
     * @param semiRecords 半成品入库记录
     */
    private void judgeInventory(List<SemiRecordDTO> semiRecords, Integer operatorId) {
        List<Product> products = productMapper.selectBatchIds(semiRecords.stream().map(SemiRecordDTO::getSemiProductId).distinct().toList());
        Map<Integer, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, s -> s));
        for (SemiRecordDTO semiRecord : semiRecords) {
            Integer warehouseId = semiRecord.getWarehouseId();
            LocalDate productionDate = semiRecord.getProductionDate();
            Product product = productMap.get(semiRecord.getSemiProductId());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND.getCode(), "半成品:" + semiRecord.getProductName() + "已用完");
            }
            VInventorySummary inventorySummary = inventorySummaryMapper.selectOne(
                    new LambdaQueryWrapper<VInventorySummary>()
                            .eq(VInventorySummary::getWarehouseId, warehouseId)
                            .eq(VInventorySummary::getProductId, semiRecord.getSemiProductId())
                            .eq(VInventorySummary::getEntryDate, productionDate)
                            .last("limit 1")
            );
            String msg = "半成品:" + semiRecord.getProductName() + ",生产日期:" + semiRecord.getProductionDate();
            if (inventorySummary == null) {
                throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND.getCode(), msg + "的库存信息未找到");
            }
            String inventoryMsg = (inventorySummary.getTotalQuantity() > 0 ? inventorySummary.getTotalQuantity() + "板" : "") + (inventorySummary.getTotalPieces() > 0 ? inventorySummary.getTotalPieces() + "件" : "");
            // 0，整板，1散件
            if (semiRecord.getUnit().equals("0")) {
                if ((inventorySummary.getTotalQuantity() + inventorySummary.getTotalPieces() / product.getPiecesPerPallet()) < semiRecord.getQuantity()) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), msg + "的库存不足,剩余:" + inventoryMsg);
                }
            } else {
                //散件
                if ((inventorySummary.getTotalQuantity() * product.getPiecesPerPallet() + inventorySummary.getTotalPieces()) < semiRecord.getQuantity()) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK.getCode(), msg + "的库存不足,剩余:" + inventoryMsg);
                }
            }
            // 扣减半成品数量
            outStockService.outStock(product, warehouseId, semiRecord.getProductionDate(), semiRecord.getQuantity(), semiRecord.getUnit(), operatorId, 1);
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

    //校验某个数值是否符合指标
    private boolean checkValue(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null && min == null && max == null) {
            return true; // 化验数据为空，且标准里上下限都为空，则无需校验
        } else if (value == null) {
            return false; // 化验数据为空，则不合格
        }
        if (min != null && max == null) {
            return value.compareTo(min) >= 0;  // 只有下限，必须大于等于下限
        }
        if (min == null && max != null) {
            return value.compareTo(max) <= 0;  // 只有上限，必须小于等于上限
        }
        if (min != null && max != null) {
            return value.compareTo(min) >= 0 && value.compareTo(max) <= 0; // 同时存在上下限
        }
        return true; // 如果标准里上下限都为空，则默认合格
    }

    // 入库操作（使用坐标方式）
//    @Transactional
//    @Override
//    public void handleStockIn(InStockRequestDTO request, Integer operatorId) {
//        Product product = productMapper.selectById(request.getProductId());
//        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
//
//        // 2. 计算总数量
//        Integer totalQuantity = request.getLocations().stream()
//                .map(InStockRequestDTO.LocationDTO::getQuantity)
//                .reduce(0, Integer::sum);
//
//        // 获取化验记录
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
//        // 3. 创建入库记录
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
//        // 查询是否已存在库存记录
//        if(inventory != null) {
//            // 更新库存主记录
//            inventoryMapper.updateInventory(
//                    null,
//                    inventory.getTotalQuantity() + totalQuantity,
//                    null,
//                    null,
//                    inventory.getInStockId(),
//                    request.getScreenMeshId()
//            );
//        } else {
//            // 创建库存主记录
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
//        // 5. 插入或更新库存位置记录（批量操作）
//        List<InventoryLocation> locationsToInsert = new ArrayList<>();
//        for (InStockRequestDTO.LocationDTO dto : request.getLocations()) {
//            // 查询是否已存在该位置
//            InventoryLocation location = inventoryLocationMapper.selectForUpdate(
//                    inventory.getId(),
//                    dto.getCoordinates().getX(),
//                    dto.getCoordinates().getY()
//            );
//
//            if (location == null) {
//                // 如果位置记录不存在，新增记录
//                location = new InventoryLocation();
//                location.setInventoryId(inventory.getId());
//                location.setCoordinateX(dto.getCoordinates().getX());
//                location.setCoordinateY(dto.getCoordinates().getY());
//                location.setQuantity(dto.getQuantity());
//                locationsToInsert.add(location);
//            } else {
//                // 如果位置记录已存在，更新数量
//                inventoryLocationMapper.AddQuantity(location.getId(), dto.getQuantity());
//            }
//        }
//
//        // 批量插入新的库存位置记录
//        if (!locationsToInsert.isEmpty()) {
//            if (inventoryLocationMapper.batchInsert(locationsToInsert) < 1) {
//                throw new BusinessException(ErrorCode.INSERT_INVENTORY_LOCATION_FAILED);
//            }
//        }
//    }

    @Override
    public PageResult<InStockVO> queryInStockRecords(InStockQueryDTO queryDTO, User currentUser) {
        // 计算分页偏移量
        int offset = (queryDTO.getPage() - 1) * queryDTO.getSize();

        // 判断是否为员工
        Boolean isStaff = currentUser.getRoleCode().equals("STAFF");

        // 获取分页数据
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

        // 获取总记录数
        Long total = inStockMapper.countInStockRecords(
                queryDTO,
                currentUser.getId(),
                isStaff
        );
        System.out.println("total:" + total);

        return new PageResult<>(total, records);
    }

    private Assay getAssayByProductIdAndDate(Integer productId, LocalDate date) {
        // 查找当天的化验记录
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
            return "[]"; // 发生异常时，返回空 JSON
        }
    }
}
