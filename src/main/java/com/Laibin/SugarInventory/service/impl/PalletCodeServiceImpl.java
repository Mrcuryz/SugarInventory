package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.BindTaskSemiItemsDTO;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.dto.SemiRecordDTO;
import com.Laibin.SugarInventory.domain.dto.TaskSemiItemDTO;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletFlowRecord;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.domain.po.PalletTaskSemiItem;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.PalletAssayVO;
import com.Laibin.SugarInventory.domain.vo.PalletBindResultVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodeInfoVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.TaskSemiItemVO;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeQueryMapper;
import com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskQueryMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskSemiItemMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ScreenMeshMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.service.InStockService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.Laibin.SugarInventory.util.PalletCodeGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * 托盘码 服务实现类
 * </p>
 *
 * @author Mrcury
 * @since 2025-12-08
 */
@Service
public class PalletCodeServiceImpl extends ServiceImpl<PalletCodeMapper, PalletCode> implements PalletCodeService, LoggableService<PalletCode> {

    private static final int MAX_BATCH_SIZE = 100;

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ScreenMeshMapper screenMeshMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PalletCodeQueryMapper palletCodeQueryMapper;
    @Autowired
    private AssayMapper assayMapper;
    @Autowired
    private InventoryMapper inventoryMapper;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private PalletTaskMapper palletTaskMapper;
    @Autowired
    private PalletFlowRecordMapper palletFlowRecordMapper;
    @Autowired
    private PalletTaskSemiItemMapper palletTaskSemiItemMapper;
    @Autowired
    private PalletTaskQueryMapper palletTaskQueryMapper;
    @Autowired
    private InStockService inStockService;
    @Autowired
    private SemiProductRecordService semiProductRecordService;

    // 批量生成托盘码：先插入占位记录拿自增ID，再生成编码回写
    @Override
    @Transactional
    public List<PalletCode> generateCodes(int count, Integer userId) {
        if (count <= 0 || count > MAX_BATCH_SIZE) {
            throw new BusinessException("生成数量必须在1-" + MAX_BATCH_SIZE + "之间");
        }

        List<PalletCode> palletCodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            PalletCode palletCode = new PalletCode();
            palletCode.setStatus("FREE");
            palletCode.setCreatedBy(userId);
            palletCode.setCreatedAt(LocalDateTime.now());
            this.baseMapper.insert(palletCode);

            String code = PalletCodeGenerator.generateCode(palletCode.getId().longValue());
            palletCode.setCode(code);

            this.baseMapper.updateById(palletCode);
            palletCodes.add(palletCode);
        }
        return palletCodes;
    }

    @Override
    public PalletCode findById(Integer id) {
        return this.getById(id);
    }

    // 解析托盘码并校验格式/校验位，随后查询托盘记录
    @Override
    public PalletCode parseAndFind(String rawCode) {
        String code = rawCode == null ? null : rawCode.trim().toUpperCase();
        if (!PalletCodeGenerator.isValidFormat(code)) {
            throw new BusinessException("托盘码格式非法");
        }
        if (!PalletCodeGenerator.verifyCheckChar(code)) {
            throw new BusinessException("托盘码校验失败");
        }
        PalletCode palletCode = this.lambdaQuery()
                .eq(PalletCode::getCode, code)
                .one();
        if (palletCode == null) {
            throw new BusinessException("托盘码不存在");
        }
        return palletCode;
    }

    // 托盘码解析 + 关联信息补全
    @Override
    public PalletCodeInfoVO parseAndGetInfo(String rawCode) {
        PalletCode palletCode = parseAndFind(rawCode);
        PalletCodeInfoVO vo = new PalletCodeInfoVO();
        vo.setId(palletCode.getId());
        vo.setCode(palletCode.getCode());
        vo.setStatus(palletCode.getStatus());
        vo.setProductStatus(palletCode.getProductStatus());
        vo.setProductionDate(palletCode.getProductionDate());
        vo.setAssayId(palletCode.getAssayId());
        vo.setCreatedAt(palletCode.getCreatedAt());
        vo.setUpdatedAt(palletCode.getUpdatedAt());

        if (palletCode.getProductId() != null) {
            Product product = productMapper.selectById(palletCode.getProductId());
            if (product != null) {
                vo.setProductName(product.getProductName());
            }
        }

        if (palletCode.getScreenMeshId() != null) {
            ScreenMesh screenMesh = screenMeshMapper.selectById(palletCode.getScreenMeshId());
            if (screenMesh != null) {
                vo.setScreenMeshName(screenMesh.getMeshName());
            }
        }

        if (palletCode.getCreatedBy() != null) {
            User creator = userMapper.selectById(palletCode.getCreatedBy());
            if (creator != null) {
                vo.setCreatedBy(creator.getName());
            }
        }

        if (palletCode.getUpdatedBy() != null) {
            User updater = userMapper.selectById(palletCode.getUpdatedBy());
            if (updater != null) {
                vo.setUpdatedBy(updater.getName());
            }
        }

        return vo;
    }

    // 查询托盘当前库存位置：需要库存表存在 pallet_code_id
    @Override
    public PalletInventoryVO getInventoryByCode(String code) {
        PalletCode palletCode = parseAndFind(code);
        Inventory inventory = this.lambdaQueryInventoryByPalletId(palletCode.getId());
        if (inventory == null) {
            throw new BusinessException("产品未入库");
        }
        Warehouse warehouse = null;
        if (inventory.getWarehouseId() != null) {
            warehouse = warehouseMapper.selectById(inventory.getWarehouseId());
        }
        PalletInventoryVO vo = new PalletInventoryVO();
        vo.setWarehouseName(warehouse != null ? warehouse.getWarehouseName() : null);
        vo.setSide(inventory.getSide());
        vo.setRowNumber(inventory.getRowNumber());
        vo.setLayer(inventory.getLayer());
        // unit: false -> 板, true -> 件
        if (inventory.getPieces() != null && inventory.getPieces() > 0) {
            vo.setUnit(Boolean.TRUE);
            vo.setQuantity(inventory.getPieces());
        } else { 
            vo.setUnit(Boolean.FALSE);
            vo.setQuantity(inventory.getQuantity() == null ? 0 : inventory.getQuantity());
        }
        vo.setInStockTime(inventory.getCreatedAt());
        return vo;
    }

    // 根据托盘码ID查库存记录（limit 1）
    private Inventory lambdaQueryInventoryByPalletId(Integer palletCodeId) {
        return this.inventoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Inventory>()
                        .eq("pallet_code_id", palletCodeId)
                        .last("limit 1")
        );
    }

    /**
     * 获取托盘关联的化验ID：优先托盘自身，其次同托盘任务中的assayId，再按产品+生产日期查最新版本，并回写托盘。
     */
    private Integer resolveAssayIdWithFallback(PalletCode palletCode) {
        if (palletCode.getAssayId() != null) {
            return palletCode.getAssayId();
        }
        PalletTask latestWithAssay = palletTaskMapper.selectLatestWithAssay(palletCode.getId());
        if (latestWithAssay != null && latestWithAssay.getAssayId() != null) {
            palletCode.setAssayId(latestWithAssay.getAssayId());
            this.updateById(palletCode);
            return latestWithAssay.getAssayId();
        }
        if (palletCode.getProductId() == null || palletCode.getProductionDate() == null) {
            return null;
        }
        Assay assay = assayMapper.selectByProductIdAndDate(palletCode.getProductId(), palletCode.getProductionDate());
        if (assay == null) {
            return null;
        }
        palletCode.setAssayId(assay.getId());
        this.updateById(palletCode);
        return assay.getId();
    }

    /**
     * 更新最近一条托盘流转记录的化验ID（若存在流转记录）。
     */
    private void updateLatestFlowAssay(Integer palletCodeId, Integer assayId) {
        PalletFlowRecord latest = palletFlowRecordMapper.selectOne(
                new LambdaQueryWrapper<PalletFlowRecord>()
                        .eq(PalletFlowRecord::getPalletCodeId, palletCodeId)
                        .orderByDesc(PalletFlowRecord::getId)
                        .last("limit 1")
        );
        if (latest != null) {
            latest.setAssayId(assayId);
            palletFlowRecordMapper.updateById(latest);
        }
    }

    // 绑定托盘并生成入库任务：校验托盘/产品，防重复任务，落表 pallet_task + 更新 pallet_code + 写入流转记录
    @Override
    @Transactional
    public PalletBindResultVO bindPalletAndCreateTask(BindPalletTaskDTO dto, Integer operatorId) {
        PalletCode palletCode = parseAndFind(dto.getCode());
        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("托盘当前状态不可绑定（仅允许 FREE 状态绑定）");
        }
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        Integer screenMeshId = product.getScreenMeshId();

        long pending = palletTaskMapper.countPendingInTasks(palletCode.getId());
        if (pending > 0) {
            throw new BusinessException("该托盘已存在未完成的入库任务");
        }

        String productStatus = dto.getProductStatus();
        String taskType;
        if ("半成品".equals(productStatus)) {
            taskType = "SEMI_IN";
        } else if ("成品".equals(productStatus)) {
            taskType = "FINISH_IN";
        } else {
            throw new BusinessException("产品状态非法");
        }

        PalletTask task = new PalletTask();
        task.setPalletCodeId(palletCode.getId());
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setProductId(dto.getProductId());
        task.setProductStatus(productStatus);
        task.setProductionDate(dto.getProductionDate());
        task.setScreenMeshId(screenMeshId);
        task.setAssayId(null);
        task.setCreatedBy(operatorId);
        task.setCreatedAt(LocalDateTime.now());
        task.setRemark(dto.getRemark());
        palletTaskMapper.insert(task);

        palletCode.setStatus("PENDING");
        palletCode.setProductId(dto.getProductId());
        palletCode.setProductStatus(productStatus);
        palletCode.setProductionDate(dto.getProductionDate());
        palletCode.setScreenMeshId(screenMeshId);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(LocalDateTime.now());
        this.updateById(palletCode);

        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setOperationType("半成品".equals(productStatus) ? "SEMI_BIND" : "FINISH_BIND");
        flow.setOperationName("半成品".equals(productStatus) ? "绑定半成品" : "绑定成品");
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(product.getId());
        flow.setProductStatus(productStatus);
        flow.setAssayId(null);
        flow.setRemark(dto.getRemark());
        palletFlowRecordMapper.insert(flow);

        PalletBindResultVO vo = new PalletBindResultVO();
        vo.setPalletCodeId(palletCode.getId());
        vo.setCode(palletCode.getCode());
        vo.setPalletStatus(palletCode.getStatus());
        vo.setTaskId(task.getId());
        vo.setTaskType(task.getTaskType());
        vo.setTaskStatus(task.getStatus());
        vo.setProductId(product.getId());
        vo.setProductName(product.getProductName());
        vo.setProductType(product.getProductType());
        vo.setProductStatus(productStatus);
        vo.setScreenMeshId(screenMeshId);
        if (screenMeshId != null) {
            ScreenMesh sm = screenMeshMapper.selectById(screenMeshId);
            vo.setScreenMeshName(sm != null ? sm.getMeshName() : null);
        }
        vo.setProductionDate(dto.getProductionDate());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setRemark(dto.getRemark());
        return vo;
    }

    @Override
    @Transactional
    public List<TaskSemiItemVO> bindSemiItemsToTask(BindTaskSemiItemsDTO dto, Integer operatorId) {
        // 为成品入库任务绑定半成品明细：全量覆盖，校验半成品托盘状态与 useAssay 唯一性
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("明细不能为空");
        }
        PalletCode finishedPallet = parseAndFind(dto.getCode());
        // 查找成品待办任务（FINISH_IN + PENDING）
        PalletTask task = palletTaskMapper.selectOne(
                new LambdaQueryWrapper<PalletTask>()
                        .eq(PalletTask::getPalletCodeId, finishedPallet.getId())
                        .eq(PalletTask::getTaskType, "FINISH_IN")
                        .eq(PalletTask::getStatus, "PENDING")
                        .last("limit 1")
        );
        if (task == null) {
            throw new BusinessException("未找到待处理的成品入库任务");
        }

        int useAssayCount = 0;
        Integer selectedAssayId = null;
        List<PalletTaskSemiItem> entities = new ArrayList<>();
        List<TaskSemiItemVO> voList = new ArrayList<>();

        for (TaskSemiItemDTO itemDTO : dto.getItems()) {
            // 累计 useAssay 数量，校验托盘状态
            boolean useAssay = Boolean.TRUE.equals(itemDTO.getUseAssay());
            if (useAssay) useAssayCount++;
            PalletCode semiPallet = parseAndFind(itemDTO.getSemiPalletCode());
            if (!"半成品".equals(semiPallet.getProductStatus())) {
                throw new BusinessException("仅允许绑定半成品托盘");
            }
            if (!"INSTOCK".equalsIgnoreCase(semiPallet.getStatus()) && !"CONSUMED".equalsIgnoreCase(semiPallet.getStatus())) {
                throw new BusinessException("半成品托盘未在库，无法使用");
            }

            Integer assayId = null;
            if (useAssay) {
                assayId = resolveAssayIdWithFallback(semiPallet);
                if (assayId == null) {
                    throw new BusinessException("找不到化验数据");
                }
                selectedAssayId = assayId;
            }

            PalletTaskSemiItem entity = new PalletTaskSemiItem();
            entity.setPalletTaskId(task.getId());
            entity.setSemiPalletCodeId(semiPallet.getId());
            entity.setSemiProductId(semiPallet.getProductId());
            entity.setProductionDate(semiPallet.getProductionDate());
            entity.setQuantity(itemDTO.getQuantity());
            entity.setUnit(itemDTO.getUnit());
            entity.setUseAssay(useAssay);
            entities.add(entity);

            TaskSemiItemVO vo = new TaskSemiItemVO();
            vo.setSemiPalletCode(semiPallet.getCode());
            vo.setSemiProductId(semiPallet.getProductId());
            Product semiProduct = semiPallet.getProductId() != null ? productMapper.selectById(semiPallet.getProductId()) : null;
            vo.setSemiProductName(semiProduct != null ? semiProduct.getProductName() : null);
            vo.setProductionDate(semiPallet.getProductionDate());
            vo.setQuantity(itemDTO.getQuantity());
            vo.setUnit(itemDTO.getUnit());
            vo.setUseAssay(useAssay);
            voList.add(vo);
        }

        if (useAssayCount > 1) {
            throw new BusinessException("仅允许一条明细使用化验数据");
        }

        palletTaskSemiItemMapper.deleteByTaskId(task.getId());
        for (int i = 0; i < entities.size(); i++) {
            PalletTaskSemiItem entity = entities.get(i);
            palletTaskSemiItemMapper.insert(entity);
            voList.get(i).setId(entity.getId());
        }

        // 如果选择套用化验数据，则把化验ID写回成品托盘 / 任务 / 最近的流转记录
        if (selectedAssayId != null) {
            finishedPallet.setAssayId(selectedAssayId);
            this.updateById(finishedPallet);
            task.setAssayId(selectedAssayId);
            palletTaskMapper.updateById(task);
            updateLatestFlowAssay(finishedPallet.getId(), selectedAssayId);
        }
        return voList;
    }

    @Override
    // 简单分页：计算 offset，走自定义 Mapper 的 limit
    public PageResult<PalletCodePageVO> pagePalletCodes(PalletCodeQueryDTO queryDTO) {
        long pageNum = queryDTO.getPageNum() == null || queryDTO.getPageNum() <= 0 ? 1L : queryDTO.getPageNum();
        long pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() <= 0 ? 10L : queryDTO.getPageSize();
        long offset = (pageNum - 1) * pageSize;
        List<PalletCodePageVO> records = palletCodeQueryMapper.pagePalletCodes(queryDTO, offset, pageSize);
        Long total = palletCodeQueryMapper.countPalletCodes(queryDTO);
        return new PageResult<>(total, records);
    }

    @Override
    public PageResult<PalletTaskPageVO> pagePalletTasks(PalletTaskQueryDTO queryDTO) {
        long pageNum = queryDTO.getPageNum() == null || queryDTO.getPageNum() <= 0 ? 1L : queryDTO.getPageNum();
        long pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() <= 0 ? 10L : queryDTO.getPageSize();
        long offset = (pageNum - 1) * pageSize;
        List<PalletTaskPageVO> records = palletTaskQueryMapper.pageTasks(queryDTO, offset, pageSize);
        for (PalletTaskPageVO vo : records) {
            vo.setHasSemiItems(vo.getSemiItemCount() != null && vo.getSemiItemCount() > 0);
        }
        Long total = palletTaskQueryMapper.countTasks(queryDTO);
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public InVO confirmSingleFinishedTaskIn(ConfirmPalletInItemDTO dto, Integer operatorId) {
        // 1) 解析托盘码并校验当前状态
        PalletCode palletCode = parseAndFind(dto.getCode());
        if (!"PENDING".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("当前托盘状态不支持入库确认");
        }
        PalletTask task = findPendingInboundTask(palletCode.getId());
        if (task == null) {
            throw new BusinessException("未找到待处理的入库任务");
        }
        // 2) 入库日期：优先前端 -> 任务生产日期 -> 托盘生产日期
        LocalDate entryDate = dto.getEntryDate() != null ? dto.getEntryDate() : task.getProductionDate();
        if (entryDate == null) {
            entryDate = palletCode.getProductionDate();
        }
        if (entryDate == null) {
            throw new BusinessException("无法确定入库日期");
        }
        // 3) 标准化侧、单位、数量
        String side = normalizeSide(dto.getSide());
        String unit = normalizeUnit(dto.getUnit());
        int quantity = normalizeQuantity(dto.getQuantity());
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }
        if (!"0".equals(unit) && !"1".equals(unit)) {
            throw new BusinessException("单位仅支持0=板或1=件");
        }
        // 4) 按任务类型分支处理
        if ("SEMI_IN".equals(task.getTaskType())) {
            return handleSemiInTask(dto.getRemark(), palletCode, task, warehouse, entryDate, side, unit, quantity, operatorId);
        }
        if ("FINISH_IN".equals(task.getTaskType())) {
            return handleFinishInTask(dto.getRemark(), palletCode, task, warehouse, entryDate, side, unit, quantity, operatorId);
        }
        throw new BusinessException("任务类型不支持入库确认");
    }

    @Override
    @Transactional
    public List<InVO> confirmFinishedTaskInBatch(ConfirmPalletInBatchDTO dto, Integer operatorId) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("入库列表不能为空");
        }
        // 逐条确认；如中途失败触发事务回滚
        List<InVO> results = new ArrayList<>();
        for (ConfirmPalletInItemDTO item : dto.getItems()) {
            results.add(confirmSingleFinishedTaskIn(item, operatorId));
        }
        return results;
    }

    private InVO handleSemiInTask(String remark, PalletCode palletCode, PalletTask task,
                                  Warehouse warehouse, LocalDate entryDate,
                                  String side, String unit, int quantity, Integer operatorId) {
        // 半成品任务：组装 AddSemiProductRecordDTO，走半成品入库链路
        Product product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        Integer assayId = ensureAssayId(palletCode, task, entryDate);
        if (assayId == null) {
            throw new BusinessException("找不到化验数据");
        }
        AddSemiProductRecordDTO recordDTO = new AddSemiProductRecordDTO();
        recordDTO.setProductId(task.getProductId());
        recordDTO.setWarehouseName(warehouse.getWarehouseName());
        recordDTO.setEntryDate(entryDate);
        recordDTO.setQuantity(quantity);
        recordDTO.setUnit(unit);
        recordDTO.setSide(side);
        recordDTO.setScreenMeshId(task.getScreenMeshId() != null ? task.getScreenMeshId() : product.getScreenMeshId());
        String operatorName = getOperatorName(operatorId);
        InVO result = semiProductRecordService.addSemiProductRecord(recordDTO, operatorName);
        // 入库成功后同步托盘/任务/流转记录
        finalizeTask(palletCode, task, assayId, warehouse, side, remark, operatorId);
        return result;
    }

    private InVO handleFinishInTask(String remark, PalletCode palletCode, PalletTask task,
                                    Warehouse warehouse, LocalDate entryDate,
                                    String side, String unit, int quantity, Integer operatorId) {
        // 成品任务：读取半成品明细、校验 useAssay 唯一，调用 stockIn
        Product product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        List<SemiRecordDTO> semiRecords = buildSemiRecords(task);
        validateUseAssayCount(semiRecords);
        Integer assayId = ensureAssayId(palletCode, task, entryDate);
        if (assayId == null) {
            throw new BusinessException("找不到化验数据");
        }
        InStockRequestDTO requestDTO = new InStockRequestDTO();
        requestDTO.setProductId(task.getProductId());
        requestDTO.setWarehouseName(warehouse.getWarehouseName());
        requestDTO.setEntryDate(entryDate);
        requestDTO.setQuantity(quantity);
        requestDTO.setUnit(unit);
        requestDTO.setSide(side);
        requestDTO.setScreenMeshId(task.getScreenMeshId() != null ? task.getScreenMeshId() : product.getScreenMeshId());
        requestDTO.setReturnInStockFlag("0");
        requestDTO.setSemiRecords(semiRecords == null ? new ArrayList<>() : semiRecords);
        InVO result = inStockService.stockIn(requestDTO, operatorId);
        // 入库成功后同步托盘/任务/流转记录
        finalizeTask(palletCode, task, assayId, warehouse, side, remark, operatorId);
        return result;
    }

    private List<SemiRecordDTO> buildSemiRecords(PalletTask task) {
        List<PalletTaskSemiItem> semiItems = palletTaskSemiItemMapper.selectList(
                new LambdaQueryWrapper<PalletTaskSemiItem>()
                        .eq(PalletTaskSemiItem::getPalletTaskId, task.getId())
        );
        if (semiItems == null || semiItems.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> productIds = semiItems.stream()
                .map(PalletTaskSemiItem::getSemiProductId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Product> productMap = productIds.isEmpty()
                ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<SemiRecordDTO> semiRecords = new ArrayList<>();
        for (PalletTaskSemiItem item : semiItems) {
            // 根据托盘明细取托盘、库存位置，用于扣减半成品库存
            PalletCode semiPallet = this.getById(item.getSemiPalletCodeId());
            if (semiPallet == null) {
                throw new BusinessException("半成品托盘不存在");
            }
            Inventory inventory = lambdaQueryInventoryByPalletId(semiPallet.getId());
            if (inventory == null || inventory.getWarehouseId() == null) {
                throw new BusinessException("半成品托盘不在库，无法扣减库存");
            }
            SemiRecordDTO record = new SemiRecordDTO();
            record.setSemiProductId(item.getSemiProductId());
            Product semiProduct = productMap.get(item.getSemiProductId());
            record.setProductName(semiProduct != null ? semiProduct.getProductName() : null);
            record.setProductionDate(item.getProductionDate() != null ? item.getProductionDate() : semiPallet.getProductionDate());
            record.setWarehouseId(inventory.getWarehouseId());
            record.setQuantity(item.getQuantity());
            record.setUnit(item.getUnit());
            record.setUseAssay(Boolean.TRUE.equals(item.getUseAssay()));
            semiRecords.add(record);
        }
        return semiRecords;
    }

    private void validateUseAssayCount(List<SemiRecordDTO> semiRecords) {
        long count = semiRecords == null ? 0 : semiRecords.stream()
                .filter(record -> Boolean.TRUE.equals(record.getUseAssay()))
                .count();
        if (count > 1) {
            throw new BusinessException("仅允许一条明细使用化验数据");
        }
    }

    private void finalizeTask(PalletCode palletCode, PalletTask task, Integer assayId, Warehouse warehouse,
                              String side, String remark, Integer operatorId) {
        LocalDateTime now = LocalDateTime.now();
        palletCode.setStatus("INSTOCK");
        palletCode.setAssayId(assayId);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(now);
        this.updateById(palletCode);
        if (remark != null && !remark.isEmpty()) {
            task.setRemark(remark);
        }
        task.setAssayId(assayId);
        task.setStatus("CONFIRMED");
        task.setConfirmedBy(operatorId);
        task.setConfirmedAt(now);
        palletTaskMapper.updateById(task);
        updateLatestFlowAssay(palletCode.getId(), assayId);
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setOperationType("SEMI_IN".equals(task.getTaskType()) ? "SEMI_INSTOCK" : "FINISH_INSTOCK");
        flow.setOperationName("SEMI_IN".equals(task.getTaskType()) ? "半成品入库" : "成品入库");
        flow.setOperationTime(now);
        flow.setOperatorId(operatorId);
        flow.setProductId(task.getProductId());
        flow.setProductStatus(task.getProductStatus());
        flow.setAssayId(assayId);
        flow.setToWarehouseId(warehouse.getId());
        flow.setToSide(side);
        flow.setRemark(remark);
        palletFlowRecordMapper.insert(flow);
    }

    private PalletTask findPendingInboundTask(Integer palletCodeId) {
        return palletTaskMapper.selectOne(
                new LambdaQueryWrapper<PalletTask>()
                        .eq(PalletTask::getPalletCodeId, palletCodeId)
                        .eq(PalletTask::getStatus, "PENDING")
                        .in(PalletTask::getTaskType, List.of("SEMI_IN", "FINISH_IN"))
                        .last("limit 1")
        );
    }

    private String getOperatorName(Integer operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作人不存在");
        }
        return operator.getName();
    }

    private Integer ensureAssayId(PalletCode palletCode, PalletTask task, LocalDate entryDate) {
        Integer assayId = palletCode.getAssayId();
        if (assayId == null && task != null) {
            assayId = task.getAssayId();
        }
        if (assayId == null) {
            assayId = resolveAssayIdWithFallback(palletCode);
        }
        // 兜底：按产品+入库日期查询化验并回填托盘/任务
        if (assayId == null && palletCode.getProductId() != null && entryDate != null) {
            Assay assay = assayMapper.selectByProductIdAndDate(palletCode.getProductId(), entryDate);
            if (assay != null) {
                assayId = assay.getId();
                palletCode.setAssayId(assayId);
                this.updateById(palletCode);
            }
        }
        if (assayId != null && task != null && !Objects.equals(task.getAssayId(), assayId)) {
            task.setAssayId(assayId);
            palletTaskMapper.updateById(task);
        }
        return assayId;
    }

    private String normalizeSide(String side) {
        return (side == null || side.isBlank()) ? "左" : side;
    }

    private String normalizeUnit(String unit) {
        return (unit == null || unit.isBlank()) ? "0" : unit;
    }

    private int normalizeQuantity(Integer quantity) {
        return quantity == null || quantity <= 0 ? 1 : quantity;
    }

    @Override
    @Transactional
    public void invalidatePalletCodes(CancelPalletBatchDTO dto, Integer operatorId) {
        if (dto == null || dto.getCodes() == null || dto.getCodes().isEmpty()) {
            throw new BusinessException("托盘码列表不能为空");
        }
        for (String rawCode : dto.getCodes()) {
            PalletCode palletCode = parseAndFind(rawCode);
            palletCode.setStatus("INVALID");
            palletCode.setUpdatedBy(operatorId);
            palletCode.setUpdatedAt(LocalDateTime.now());
            this.updateById(palletCode);
            // 取消关联任务
            cancelTasksForPallet(palletCode.getId(), operatorId, dto.getRemark());
            // 作废流转记录
            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCode.getId());
            flow.setOperationType("CANCELED");
            flow.setOperationName("作废托盘码");
            flow.setOperationTime(LocalDateTime.now());
            flow.setOperatorId(operatorId);
            flow.setProductId(palletCode.getProductId());
            flow.setProductStatus(palletCode.getProductStatus());
            flow.setAssayId(palletCode.getAssayId());
            flow.setRemark(dto.getRemark());
            palletFlowRecordMapper.insert(flow);
        }
    }

    @Override
    @Transactional
    public void cancelTasksByCodes(CancelPalletBatchDTO dto, Integer operatorId) {
        if (dto == null || dto.getCodes() == null || dto.getCodes().isEmpty()) {
            throw new BusinessException("托盘码列表不能为空");
        }
        for (String rawCode : dto.getCodes()) {
            PalletCode palletCode = parseAndFind(rawCode);
            // 先取消任务并写入流转
            cancelTasksForPallet(palletCode.getId(), operatorId, dto.getRemark());
        }
        // 作废托盘码（内部再次幂等取消任务）
        invalidatePalletCodes(dto, operatorId);
    }

    private void cancelTasksForPallet(Integer palletCodeId, Integer operatorId, String remark) {
        List<PalletTask> tasks = palletTaskMapper.selectList(
                new LambdaQueryWrapper<PalletTask>()
                        .eq(PalletTask::getPalletCodeId, palletCodeId)
                        .ne(PalletTask::getStatus, "CANCELED")
        );
        LocalDateTime now = LocalDateTime.now();
        for (PalletTask task : tasks) {
                              task.setStatus("CANCELED");
            palletTaskMapper.updateById(task);
            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCodeId);
            flow.setOperationType("CANCELED");
            flow.setOperationName("取消入库任务");
            flow.setOperationTime(now);
            flow.setOperatorId(operatorId);
            flow.setProductId(task.getProductId());
            flow.setProductStatus(task.getProductStatus());
            flow.setAssayId(task.getAssayId());
            flow.setRemark(remark);
            palletFlowRecordMapper.insert(flow);
        }
    }

    @Override
    // 通过托盘码关联化验；若缺失则按产品ID+生产日期补查最新版本，再回写托盘
    public PalletAssayVO getAssayByCode(String code) {
        PalletCode palletCode = parseAndFind(code);
        Integer assayId = resolveAssayIdWithFallback(palletCode);
        if (assayId == null) {
            throw new BusinessException("找不到化验数据");
        }
        Assay assay = assayMapper.selectById(assayId);
        if (assay == null) {
            throw new BusinessException("找不到化验数据");
        }
        PalletAssayVO vo = new PalletAssayVO();
        vo.setSampleDate(assay.getSampleDate());
        vo.setColorValue(assay.getColorValue());
        vo.setReducingSugar(assay.getReducingSugar());
        vo.setDryWeight(assay.getDryWeight());
        vo.setConductivityAsh(assay.getConductivityAsh());
        vo.setSucrose(assay.getSucrose());
        vo.setInsolubleImpurity(assay.getInsolubleImpurity());
        vo.setPhValue(assay.getPhValue());
        vo.setIsQualified(assay.getIsQualified());
        vo.setQualifiedStandards(assay.getQualifiedStandards());
        vo.setCreatedAt(assay.getCreatedAt());
        if (assay.getProductId() != null) {
            Product product = productMapper.selectById(assay.getProductId());
            if (product != null) {
                vo.setProductName(product.getProductName());
            }
        }
        if (assay.getTestedBy() != null) {
            User tester = userMapper.selectById(assay.getTestedBy());
            if (tester != null) {
                vo.setTesterName(tester.getName());
            }
        }
        return vo;
    }

    @Override
    public String getTableName() {
        return "pallet_code";
    }
}
