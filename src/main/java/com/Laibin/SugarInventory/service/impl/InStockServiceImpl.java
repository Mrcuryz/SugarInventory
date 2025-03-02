package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.InStockQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InStockUpdateDTO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.*;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.vo.InStockVO;
import com.Laibin.SugarInventory.mapper.*;
import com.Laibin.SugarInventory.service.InStockService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
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
    private InventoryLocationMapper inventoryLocationMapper;

    private final Integer page = 1;
    private final Integer size = 10;

    @Transactional
    @Override
    public void handleStockIn(InStockRequestDTO request, Integer operatorId) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

        // 2. 计算总数量
        Integer totalQuantity = request.getLocations().stream()
                .map(InStockRequestDTO.LocationDTO::getQuantity)
                .reduce(0, Integer::sum);

        // 获取化验记录
        Assay assay = getAssayByProductIdAndDate(request.getProductId(), LocalDate.now());
        if (assay == null) {
            throw new BusinessException(ErrorCode.ASSAY_RECORD_NOT_FOUND);
        }

        SemiProductRecord semiProductRecord = semiProductRecordMapper
                .selectByProductIdAndDate(request.getSemiProductId(), request.getSemiDate());
        if(semiProductRecord == null){
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }

        // 3. 创建入库记录
        InStock inStock = new InStock();
        inStock.setProductId(request.getProductId());
        inStock.setWarehouseId(request.getWarehouseId());
        inStock.setQuantity(totalQuantity);
        inStock.setWeightPerPiece(product.getWeightPerPiece());
        inStock.setTotalWeight(product.getWeightPerPiece().multiply(new BigDecimal(totalQuantity)));
        inStock.setEntryDate(LocalDate.now());
        inStock.setAssayId(assay.getId());
        inStock.setSemiProductRecordId(semiProductRecord.getId());
        inStock.setScreenMeshId(request.getScreenMeshId());
        inStock.setCreatedAt(LocalDateTime.now());
        inStock.setCreatedBy(operatorId);

        if(inStockMapper.insert(inStock) < 1){
            throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
        }

        Inventory inventory = inventoryMapper.existSameInventory(
                request.getWarehouseId(), product.getId(), LocalDate.now(), request.getScreenMeshId());
        // 查询是否已存在库存记录
        if(inventory != null) {
            // 更新库存主记录
            inventoryMapper.updateInventory(
                    null,
                    inventory.getTotalQuantity() + totalQuantity,
                    null,
                    null,
                    inventory.getInStockId(),
                    request.getScreenMeshId()
            );
        } else {
            // 创建库存主记录
            inventory = new Inventory();
            inventory.setProductId(product.getId());
            inventory.setWarehouseId(request.getWarehouseId());
            inventory.setEntryDate(LocalDate.now());
            inventory.setInStockId(inStock.getId());
            inventory.setScreenMeshId(request.getScreenMeshId());
            inventory.setAssayId(assay.getId());
            inventory.setTotalQuantity(totalQuantity);
            inventory.setCreatedAt(LocalDateTime.now());
            if (inventoryMapper.insert(inventory) < 1){
                throw new BusinessException(ErrorCode.STOCK_IN_FAILED);
            }
        }

        // 5. 插入或更新库存位置记录（批量操作）
        List<InventoryLocation> locationsToInsert = new ArrayList<>();
        for (InStockRequestDTO.LocationDTO dto : request.getLocations()) {
            // 查询是否已存在该位置
            InventoryLocation location = inventoryLocationMapper.selectForUpdate(
                    inventory.getId(),
                    dto.getCoordinates().getX(),
                    dto.getCoordinates().getY()
            );

            if (location == null) {
                // 如果位置记录不存在，新增记录
                location = new InventoryLocation();
                location.setInventoryId(inventory.getId());
                location.setCoordinateX(dto.getCoordinates().getX());
                location.setCoordinateY(dto.getCoordinates().getY());
                location.setQuantity(dto.getQuantity());
                locationsToInsert.add(location);
            } else {
                // 如果位置记录已存在，更新数量
                inventoryLocationMapper.AddQuantity(location.getId(), dto.getQuantity());
            }
        }

        // 批量插入新的库存位置记录
        if (!locationsToInsert.isEmpty()) {
            if (inventoryLocationMapper.batchInsert(locationsToInsert) < 1) {
                throw new BusinessException(ErrorCode.INSERT_INVENTORY_LOCATION_FAILED);
            }
        }
    }

    @Override
    public PageResult<InStockVO> queryInStockRecords(InStockQueryDTO queryDTO, User currentUser) {
        // 计算分页偏移量
        int offset = (page - 1) * size;

        // 判断是否为员工
        Boolean isStaff = currentUser.getRoleCode().equals("STAFF");

        // 获取分页数据
        List<InStockVO> records = inStockMapper.selectInStockList(
                queryDTO,
                currentUser.getId(),
                isStaff,
                offset,
                size
        );

        // 获取总记录数
        Long total = inStockMapper.countInStockRecords(
                queryDTO,
                currentUser.getId(),
                isStaff
        );

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
}
