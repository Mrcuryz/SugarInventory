package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.SemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.dto.SemiRecordDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.InStockService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
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
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private InventoryLocationMapper inventoryLocationMapper;

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
        Warehouse warehouse = warehouseMapper.selectById(dto.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        Assay assay = getAssayByProductIdAndDate(dto.getProductId(), LocalDate.now());
        if (assay == null) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }

        // 3. 解析前端传来的半成品 JSON，并查询数据库
        List<Integer> semiProductRecordIds = new ArrayList<>();
        for (SemiRecordDTO recordDTO : dto.getSemiProductRecords()) {
            SemiProductRecord record = semiProductRecordMapper
                    .selectByProductIdAndDate(recordDTO.getSemiProductId(), recordDTO.getProductionDate());
            if (record == null) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
            }
            semiProductRecordIds.add(record.getId());
        }

        if(semiProductRecordIds.isEmpty())
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);

        String semiProductRecordsJson = convertToJson(semiProductRecordIds);

        int maxRows = warehouse.getMaxRows();
        int remainingQuantity = dto.getQuantity();
        String currentSide = dto.getSide(); // 默认从左侧存放
        boolean canStack = product.getCanStack(); // 是否可堆积

        // **3. 预获取当前库位的存储情况**
        int leftUsedRowsLayer1 = inventoryMapper.getUsedRows(dto.getWarehouseId(), "LEFT", 1);
        int rightUsedRowsLayer1 = inventoryMapper.getUsedRows(dto.getWarehouseId(), "RIGHT", 1);
        int leftUsedRowsLayer2 = inventoryMapper.getUsedRows(dto.getWarehouseId(), "LEFT", 2);
        int rightUsedRowsLayer2 = inventoryMapper.getUsedRows(dto.getWarehouseId(), "RIGHT", 2);

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
        InStock inStock = new InStock();
        inStock.setWarehouseId(dto.getWarehouseId());
        inStock.setProductId(dto.getProductId());
        inStock.setQuantity(quantity);
        inStock.setSemiProductRecords(semiProductRecordsJson);
        inStock.setCreatedBy(operatorId);
        inStock.setEntryDate(LocalDate.now());
        inStock.setAssayId(assay.getId());
        inStock.setScreenMeshId(dto.getScreenMeshId());
        inStock.setTotalWeight(totalWeight);
        inStock.setCreatedAt(LocalDateTime.now());

        inStockMapper.insert(inStock);

        Integer inStockId = inStock.getId();

        // **5. 开始存放**
        while (remainingQuantity > 0) {
            int usedRows = (currentSide.equals("LEFT")) ?
                    (currentLayer == 1 ? leftUsedRowsLayer1 : leftUsedRowsLayer2)
                    : (currentLayer == 1 ? rightUsedRowsLayer1 : rightUsedRowsLayer2);

            if (usedRows < maxRows) {
                int rowNumber = usedRows + 1;

                // **存储单板**
                Inventory inventory = new Inventory();
                inventory.setWarehouseId(dto.getWarehouseId());
                inventory.setProductId(dto.getProductId());
                inventory.setSide(currentSide);
                inventory.setRowNumber(rowNumber);
                inventory.setLayer(currentLayer);
                inventory.setQuantity(1);
                inventory.setEntryDate(LocalDate.now());
                inventory.setInStockId(inStockId);
                inventory.setScreenMeshId(dto.getScreenMeshId());
                inventory.setAssayId(assay.getId());
                inventory.setProductStatus(product.getStatus());
                inventory.setCreatedAt(LocalDateTime.now());

                inventoryMapper.insert(inventory);
                remainingQuantity--;

                // **更新本地变量**
                if (currentSide.equals("LEFT")) {
                    if (currentLayer == 1) leftUsedRowsLayer1++;
                    else leftUsedRowsLayer2++;
                } else {
                    if (currentLayer == 1) rightUsedRowsLayer1++;
                    else rightUsedRowsLayer2++;
                }
            }

            // **如果当前列满，尝试切换到另一侧**
            if (remainingQuantity > 0 && usedRows >= maxRows) {
                currentSide = currentSide.equals("LEFT") ? "RIGHT" : "LEFT";
                usedRows = (currentSide.equals("LEFT")) ?
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
                    InVO inVO = new InVO();
                    inVO.setRemainingQuantity(remainingQuantity);
                    inVO.setMessage("库位已满！剩余 " + remainingQuantity + " 板产品，请选择新库位");
                    return inVO;
                }
            }
        }

        // **6. 同步更新库位信息**
        warehouseMapper.updateCurCapacity(
                dto.getWarehouseId(), warehouse.getCurCapacity() + (quantity * product.getPiecesPerPallet()));
        if(product.getCanStack() && warehouse.getMaxCapacity() != warehouse.getMaxRows() * 2 * 2)
            warehouseMapper.updateMaxCapacity(dto.getWarehouseId(), warehouse.getMaxRows() * 2 * 2);

        InVO inVO = new InVO();
        inVO.setRemainingQuantity(0);
        inVO.setMessage("入库成功！");
        return inVO;
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
        System.out.println(currentUser.getRoleCode());
        System.out.println("DTO:" + queryDTO);
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
