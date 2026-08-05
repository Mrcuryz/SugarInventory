package com.Laibin.SugarInventory.service.impl;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.domain.dto.AddSemiProductRecordDTO;
import com.Laibin.SugarInventory.domain.dto.BindPalletTaskDTO;
import com.Laibin.SugarInventory.domain.dto.BindTaskSemiItemsDTO;
import com.Laibin.SugarInventory.domain.dto.CancelPalletBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmTransferBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmPalletInItemDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmFinishOutBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiConsumeBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiOutBatchDTO;
import com.Laibin.SugarInventory.domain.dto.ConfirmSemiPrepareBatchDTO;
import com.Laibin.SugarInventory.domain.dto.CreateFinishOutTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateSemiOutTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateSemiPrepareTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateTransferTaskDTO;
import com.Laibin.SugarInventory.domain.dto.CreateTransferTaskItemDTO;
import com.Laibin.SugarInventory.domain.dto.DeletePalletFlowBatchDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductActivateDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductBindDTO;
import com.Laibin.SugarInventory.domain.dto.FixedProductPoolQueryDTO;
import com.Laibin.SugarInventory.domain.dto.InStockRequestDTO;
import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.dto.PalletQrExportDTO;
import com.Laibin.SugarInventory.domain.dto.PalletTaskQueryDTO;
import com.Laibin.SugarInventory.domain.dto.SemiRecordDTO;
import com.Laibin.SugarInventory.domain.dto.TaskSemiItemDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMapBatchOperationDTO;
import com.Laibin.SugarInventory.domain.dto.WarehouseMapSlotInboundDTO;
import com.Laibin.SugarInventory.domain.bo.AssayResolveResult;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.domain.po.Assay;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.PalletCode;
import com.Laibin.SugarInventory.domain.po.PalletFlowRecord;
import com.Laibin.SugarInventory.domain.po.PalletTask;
import com.Laibin.SugarInventory.domain.po.PalletTaskSemiItem;
import com.Laibin.SugarInventory.domain.po.Product;
import com.Laibin.SugarInventory.domain.po.ProductionConsumptionRecord;
import com.Laibin.SugarInventory.domain.po.ProductionPrepareLedger;
import com.Laibin.SugarInventory.domain.po.ScreenMesh;
import com.Laibin.SugarInventory.domain.po.SemiPreparePoolBalance;
import com.Laibin.SugarInventory.domain.po.SemiPreparePool;
import com.Laibin.SugarInventory.domain.po.User;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.po.OutStock;
import com.Laibin.SugarInventory.domain.vo.InVO;
import com.Laibin.SugarInventory.domain.vo.PalletAssayVO;
import com.Laibin.SugarInventory.domain.vo.PalletBindResultVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodeInfoVO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowCyclePageVO;
import com.Laibin.SugarInventory.domain.vo.PalletFlowDetailVO;
import com.Laibin.SugarInventory.domain.vo.PalletInventoryVO;
import com.Laibin.SugarInventory.domain.vo.PalletTaskPageVO;
import com.Laibin.SugarInventory.domain.vo.TaskSemiItemVO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolVO;
import com.Laibin.SugarInventory.domain.vo.WarehouseMapTaskCreateResultVO;
import com.Laibin.SugarInventory.domain.vo.TransferTaskPreviewVO;
import com.Laibin.SugarInventory.inventoryhistory.domain.StockMovementEventCommand;
import com.Laibin.SugarInventory.inventoryhistory.service.StockMovementEventService;
import com.Laibin.SugarInventory.mapper.AssayMapper;
import com.Laibin.SugarInventory.mapper.InventoryMapper;
import com.Laibin.SugarInventory.mapper.OutStockMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeMapper;
import com.Laibin.SugarInventory.mapper.PalletCodeQueryMapper;
import com.Laibin.SugarInventory.mapper.PalletFlowRecordMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskQueryMapper;
import com.Laibin.SugarInventory.mapper.PalletTaskSemiItemMapper;
import com.Laibin.SugarInventory.mapper.ProductMapper;
import com.Laibin.SugarInventory.mapper.ProductionConsumptionRecordMapper;
import com.Laibin.SugarInventory.mapper.ProductionPrepareLedgerMapper;
import com.Laibin.SugarInventory.mapper.ScreenMeshMapper;
import com.Laibin.SugarInventory.mapper.SemiPreparePoolBalanceMapper;
import com.Laibin.SugarInventory.mapper.SemiPreparePoolMapper;
import com.Laibin.SugarInventory.mapper.UserMapper;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderOutputCode;
import com.Laibin.SugarInventory.production.domain.po.ProductionOrderLabelCode;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderLabelCodeMapper;
import com.Laibin.SugarInventory.production.mapper.ProductionOrderOutputCodeMapper;
import com.Laibin.SugarInventory.production.service.ProductionOrderService;
import com.Laibin.SugarInventory.service.InStockService;
import com.Laibin.SugarInventory.service.AssayResolveService;
import com.Laibin.SugarInventory.service.LoggableService;
import com.Laibin.SugarInventory.service.PalletCodeService;
import com.Laibin.SugarInventory.service.SemiProductRecordService;
import com.Laibin.SugarInventory.service.model.PalletInventoryOccupancyRule;
import com.Laibin.SugarInventory.service.model.ProductionPrepareLedgerSnapshot;
import com.Laibin.SugarInventory.service.model.ProductionPrepareLedgerSnapshotCalculator;
import com.Laibin.SugarInventory.util.PalletCodeGenerator;
import com.Laibin.SugarInventory.util.PalletQrLabelPdfRenderer;
import com.Laibin.SugarInventory.util.QrCodeUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

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

    private static final Logger log = LoggerFactory.getLogger(PalletCodeServiceImpl.class);
    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_TASK_TRANSITION_BATCH_SIZE = 20;
    private static final int MAX_LOCATION_RETRY = 3;
    private static final int FLOW_RETENTION_DAYS = 180;
    private static final String LEFT_SIDE = "左";
    private static final String RIGHT_SIDE = "右";
    private static final String BATCH_REMARK_PREFIX = "平面图操作批次";
    private static final String FIXED_PRODUCT_ACTIVATE_REMARK = "固定产品二维码打印并启用";

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
    private OutStockMapper outStockMapper;
    @Autowired
    private WarehouseMapper warehouseMapper;
    @Autowired
    private PalletTaskMapper palletTaskMapper;
    @Autowired
    private PalletFlowRecordMapper palletFlowRecordMapper;
    @Autowired
    private PalletTaskSemiItemMapper palletTaskSemiItemMapper;
    @Autowired
    private SemiPreparePoolMapper semiPreparePoolMapper;
    @Autowired
    private ProductionConsumptionRecordMapper productionConsumptionRecordMapper;
    @Autowired
    private ProductionPrepareLedgerMapper productionPrepareLedgerMapper;
    @Autowired
    private SemiPreparePoolBalanceMapper semiPreparePoolBalanceMapper;
    @Autowired
    private PalletTaskQueryMapper palletTaskQueryMapper;
    @Autowired
    private StockMovementEventService stockMovementEventService;
    @Autowired
    private InStockService inStockService;
    @Autowired
    private SemiProductRecordService semiProductRecordService;
    @Autowired
    private AssayResolveService assayResolveService;
    @Autowired
    private ProductionOrderOutputCodeMapper productionOrderOutputCodeMapper;
    @Autowired
    private ProductionOrderLabelCodeMapper productionOrderLabelCodeMapper;
    @Autowired
    private ProductionOrderService productionOrderService;

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
            palletCode.setCurrentCycleNo(0);
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
        String code = rawCode == null ? null : rawCode.trim();
        if (code != null && code.startsWith("LB|")) {
            return parseProductionLabelAndFind(code);
        }
        code = code == null ? null : code.toUpperCase();
        if (!PalletCodeGenerator.isValidFormat(code)) {
            throw new BusinessException(ErrorCode.INVALID_PALLET_CODE);
        }
        if (!PalletCodeGenerator.verifyCheckChar(code)) {
            throw new BusinessException(ErrorCode.INVALID_PALLET_CODE);
        }
        PalletCode palletCode = this.lambdaQuery()
                .eq(PalletCode::getCode, code)
                .one();
        if (palletCode == null) {
            throw new BusinessException(ErrorCode.PALLET_CODE_NOT_FOUND);
        }
        return palletCode;
    }

    private PalletCode parseProductionLabelAndFind(String content) {
        Map<String, String> payload = java.util.Arrays.stream(content.split("\\|"))
                .skip(1)
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (a, b) -> b));
        Long labelCodeId;
        try {
            labelCodeId = Long.parseLong(payload.getOrDefault("labelCodeId", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException("订单标签二维码内容无效");
        }
        String token = payload.get("token");
        if (token == null || token.isBlank()) {
            throw new BusinessException("订单标签二维码缺少校验令牌");
        }
        ProductionOrderLabelCode labelCode = productionOrderLabelCodeMapper.selectByIdAndToken(labelCodeId, token);
        if (labelCode == null) {
            throw new BusinessException("订单标签不存在或令牌不匹配");
        }
        if ("RESERVED".equals(labelCode.getStatus())) {
            throw new BusinessException("该订单标签尚未确认生产结束，需先由生产管理确认实际产出");
        }
        if ("RECYCLED".equals(labelCode.getStatus()) || "CANCELED".equals(labelCode.getStatus())) {
            throw new BusinessException("该订单标签已回收或失效，不能入库");
        }
        if (!"USED".equals(labelCode.getStatus())) {
            throw new BusinessException("该订单标签状态异常，不能入库");
        }
        PalletCode palletCode = this.lambdaQuery()
                .eq(PalletCode::getId, labelCode.getPalletCodeId())
                .one();
        if (palletCode == null) {
            throw new BusinessException("订单标签对应二维码不存在");
        }
        return palletCode;
    }

    private PalletCode parseAndFindForUpdate(String rawCode) {
        return parseAndFindForUpdate(rawCode, null);
    }

    private PalletCode parseAndFindForUpdate(String rawCode, Integer expectedPalletCodeId) {
        PalletCode palletCode = parseAndFind(rawCode);
        if (expectedPalletCodeId != null && !Objects.equals(expectedPalletCodeId, palletCode.getId())) {
            throw new BusinessException("托盘码关联已变化，请刷新后重试");
        }
        PalletCode locked = this.baseMapper.selectByIdForUpdate(palletCode.getId());
        if (locked == null) {
            throw new BusinessException(ErrorCode.PALLET_CODE_NOT_FOUND);
        }
        return locked;
    }

    // 托盘码解析 + 关联信息补全
    @Override
    public PalletCodeInfoVO parseAndGetInfo(String rawCode) {
        PalletCode palletCode = parseAndFind(rawCode);
        PalletCodeInfoVO vo = new PalletCodeInfoVO();
        vo.setId(palletCode.getId());
        vo.setCode(palletCode.getCode());
        vo.setStatus(palletCode.getStatus());
        vo.setFixedProductId(palletCode.getFixedProductId());
        vo.setFixedModeEnabled(Boolean.TRUE.equals(palletCode.getFixedModeEnabled()));
        vo.setCreatedAt(palletCode.getCreatedAt());
        vo.setUpdatedAt(palletCode.getUpdatedAt());

        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            vo.setProductStatus(palletCode.getProductStatus());
            vo.setProductionDate(palletCode.getProductionDate());
            vo.setAssayId(palletCode.getAssayId());
        }

        if (!"FREE".equalsIgnoreCase(palletCode.getStatus()) && palletCode.getProductId() != null) {
            Product product = productMapper.selectById(palletCode.getProductId());
            if (product != null) {
                vo.setProductName(product.getProductName());
            }
        }

        if (palletCode.getFixedProductId() != null) {
            Product fixedProduct = productMapper.selectById(palletCode.getFixedProductId());
            if (fixedProduct != null) {
                vo.setFixedProductName(fixedProduct.getProductName());
                if (vo.getProductName() == null && "FREE".equalsIgnoreCase(palletCode.getStatus())) {
                    vo.setProductName(fixedProduct.getProductName());
                }
            }
        }

        if (!"FREE".equalsIgnoreCase(palletCode.getStatus()) && palletCode.getScreenMeshId() != null) {
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

    @Override
    public byte[] generateQrCodePng(String code) {
        PalletCode palletCode = parseAndFind(code);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(QrCodeUtils.generateQrCode(palletCode.getCode(), 512, 512), "PNG", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("生成二维码 PNG 失败");
        }
    }

    @Override
    public String generateQrCodeSvg(String code) {
        PalletCode palletCode = parseAndFind(code);
        return QrCodeUtils.generateQrCodeSvg(palletCode.getCode(), 512, 512);
    }

    @Override
    public byte[] generateQrLabelPdf(PalletQrExportDTO dto) {
        List<String> codes = normalizeAndValidateUniqueCodes(dto.getCodes());
        List<String> existingCodes = new ArrayList<>(codes.size());
        for (String code : codes) {
            existingCodes.add(parseAndFind(code).getCode());
        }
        try {
            return PalletQrLabelPdfRenderer.renderA4Labels(existingCodes);
        } catch (IOException e) {
            throw new BusinessException("生成二维码标签 PDF 失败");
        }
    }

    @Override
    @Transactional
    public int bindFixedProductCodes(FixedProductBindDTO dto, Integer userId) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }

        int expectedCount = dto.getNum() == null ? 0 : dto.getNum();
        if (expectedCount <= 0) {
            throw new BusinessException("绑定数量必须大于 0");
        }

        List<PalletCode> palletCodes = this.baseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PalletCode>()
                        .eq("status", "FREE")
                        .isNull("fixed_product_id")
                        .orderByAsc("updated_at")
                        .orderByAsc("id")
                        .last("limit " + expectedCount + " for update")
        );

        if (palletCodes.size() < expectedCount) {
            throw new BusinessException("可用于初始化绑定的空闲二维码数量不足");
        }

        LocalDateTime now = LocalDateTime.now();
        for (PalletCode palletCode : palletCodes) {
            palletCode.setFixedProductId(product.getId());
            palletCode.setFixedModeEnabled(Boolean.TRUE);
            palletCode.setUpdatedBy(userId);
            palletCode.setUpdatedAt(now);
            this.baseMapper.updateById(palletCode);
        }
        return palletCodes.size();
    }

    @Override
    public byte[] generateFixedProductQrLabelPdf(PalletQrExportDTO dto) {
        List<String> codes = normalizeAndValidateUniqueCodes(dto.getCodes());
        List<PalletCode> palletCodes = new ArrayList<>(codes.size());
        for (String code : codes) {
            PalletCode palletCode = parseAndFind(code);
            validateFixedProductPrintable(palletCode);
            palletCodes.add(palletCode);
        }
        return renderFixedProductQrLabelPdf(palletCodes);
    }

    @Override
    @Transactional
    public byte[] activateFixedProductCodesAndGeneratePdf(FixedProductActivateDTO dto, Integer userId) {
        List<String> codes = normalizeAndValidateUniqueCodes(dto.getCodes());
        List<PalletCode> palletCodes = new ArrayList<>(codes.size());
        Product product = null;
        Integer fixedProductId = null;
        for (String code : codes) {
            PalletCode palletCode = parseAndFindForUpdate(code);
            validateFixedProductPrintable(palletCode);
            if (fixedProductId == null) {
                fixedProductId = palletCode.getFixedProductId();
                product = requireFixedProduct(fixedProductId);
            } else if (!Objects.equals(fixedProductId, palletCode.getFixedProductId())) {
                throw new BusinessException("打印并启用时只能选择同一产品的二维码");
            }
            palletCodes.add(palletCode);
        }

        byte[] pdf = renderFixedProductQrLabelPdf(palletCodes);
        String productStatus = resolveInboundProductStatus(product.getStatus());
        for (PalletCode palletCode : palletCodes) {
            createInboundTaskForPallet(palletCode, product, productStatus, dto.getProductionDate(), userId, FIXED_PRODUCT_ACTIVATE_REMARK);
        }
        return pdf;
    }

    // 查询托盘当前库存位置：需要库存表存在 pallet_code_id
    @Override
    public PalletInventoryVO getInventoryByCode(String code) {
        PalletCode palletCode = parseAndFind(code);
        Inventory inventory = this.lambdaQueryInventoryByPalletId(palletCode.getId());
        if (inventory == null) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND.getCode(), "产品未入库");
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
        return lambdaQueryInventoryByPalletId(palletCodeId, false);
    }

    private Inventory lambdaQueryInventoryByPalletId(Integer palletCodeId, boolean forUpdate) {
        return this.inventoryMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Inventory>()
                        .eq("pallet_code_id", palletCodeId)
                        .last(forUpdate ? "limit 1 for update" : "limit 1")
        );
    }

    /**
     * 释放托盘到 FREE 状态：清空绑定信息，保留当前轮次号。
     */
    private void releasePalletToFree(PalletCode palletCode, Integer operatorId) {
        palletCode.setStatus("FREE");
        palletCode.setProductId(null);
        palletCode.setProductStatus(null);
        palletCode.setProductionDate(null);
        palletCode.setScreenMeshId(null);
        palletCode.setAssayId(null);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(LocalDateTime.now());
        this.updateById(palletCode);
    }

    private int getCurrentCycleNo(PalletCode palletCode) {
        return palletCode == null || palletCode.getCurrentCycleNo() == null ? 0 : palletCode.getCurrentCycleNo();
    }

    /**
     * 获取托盘当前轮次关联的化验ID：仅接受当前绑定产品+生产日期匹配的化验，避免复用上一轮残留 assayId。
     */
    private Integer resolveAssayIdWithFallback(PalletCode palletCode) {
        PalletTask latestWithAssay = palletTaskMapper.selectLatestWithAssay(
                palletCode.getId(),
                getCurrentCycleNo(palletCode)
        );
        Inventory inventory = lambdaQueryInventoryByPalletId(palletCode.getId());
        AssayResolveResult result = assayResolveService.resolveForPallet(
                palletCode,
                latestWithAssay,
                inventory,
                null,
                null,
                true
        );
        return result.hasAssay() ? result.getAssay().getId() : null;
    }

    /**
     * 更新最近一条托盘流转记录的化验ID（若存在流转记录）。
     */
    private void updateLatestFlowAssay(Integer palletCodeId, Integer assayId, Integer cycleNo) {
        if (cycleNo == null) {
            return;
        }
        PalletFlowRecord latest = palletFlowRecordMapper.selectOne(
                new LambdaQueryWrapper<PalletFlowRecord>()
                        .eq(PalletFlowRecord::getPalletCodeId, palletCodeId)
                        .eq(PalletFlowRecord::getCycleNo, cycleNo)
                        .orderByDesc(PalletFlowRecord::getId)
                        .last("limit 1")
        );
        if (latest != null) {
            latest.setAssayId(assayId);
            palletFlowRecordMapper.updateById(latest);
        }
    }

    private void insertAssayFlowIfAbsent(PalletCode palletCode, PalletTask task, Integer assayId,
                                         Integer operatorId, String operationName, String remark) {
        if (palletCode == null || assayId == null) {
            return;
        }
        Integer cycleNo = getCurrentCycleNo(palletCode);
        Long existing = palletFlowRecordMapper.selectCount(new LambdaQueryWrapper<PalletFlowRecord>()
                .eq(PalletFlowRecord::getPalletCodeId, palletCode.getId())
                .eq(PalletFlowRecord::getCycleNo, cycleNo)
                .eq(PalletFlowRecord::getOperationType, "ASSAY")
                .eq(PalletFlowRecord::getAssayId, assayId));
        if (existing != null && existing > 0) {
            return;
        }
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task == null ? null : task.getId());
        flow.setOperationType("ASSAY");
        flow.setOperationName(operationName);
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus(palletCode.getProductStatus());
        flow.setAssayId(assayId);
        flow.setCycleNo(cycleNo);
        flow.setRemark(appendTraceRemark(remark, task == null ? null : "任务ID=" + task.getId()));
        palletFlowRecordMapper.insert(flow);
    }

    // 绑定托盘并生成入库任务：校验托盘/产品，防重复任务，落表 pallet_task + 更新 pallet_code + 写入流转记录
    @Override
    @Transactional
    public PalletBindResultVO bindPalletAndCreateTask(BindPalletTaskDTO dto, Integer operatorId) {
        PalletCode palletCode = parseAndFindForUpdate(dto.getCode());
        if (Boolean.TRUE.equals(palletCode.getFixedModeEnabled())) {
            if ("FREE".equalsIgnoreCase(palletCode.getStatus())) {
                throw new BusinessException("当前二维码属于固定产品模式，无需绑定产品，请在后台执行打印并启用");
            }
            if ("PENDING".equalsIgnoreCase(palletCode.getStatus())) {
                throw new BusinessException("当前二维码已由后台启用并创建任务，无需重复创建");
            }
        }
        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("托盘当前状态不可绑定（仅允许 FREE 状态绑定）");
        }
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        validateSinglePalletOccupancy(normalizeUnit(dto.getUnit()), normalizeQuantity(dto.getQuantity()), product.getPiecesPerPallet(), "二维码绑定");
        if (Boolean.TRUE.equals(palletCode.getFixedModeEnabled())) {
            if (palletCode.getFixedProductId() == null) {
                throw new BusinessException("当前二维码未配置固定产品");
            }
            if (!Objects.equals(palletCode.getFixedProductId(), dto.getProductId())) {
                throw new BusinessException("固定产品二维码不能绑定到其他产品");
            }
        }
        Integer screenMeshId = product.getScreenMeshId();

        int currentCycleNo = getCurrentCycleNo(palletCode);
        cancelPreviousCyclePendingTasks(palletCode, operatorId, "新轮次开始前自动收尾");
        long pending = palletTaskMapper.countPendingInTasks(palletCode.getId(), currentCycleNo);
        if (pending > 0) {
            throw new BusinessException("该托盘已存在未完成的入库任务");
        }

        // 新一轮绑定：cycle 递增
        int nextCycle = currentCycleNo + 1;

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
        task.setCycleNo(nextCycle);
        palletTaskMapper.insert(task);

        palletCode.setStatus("PENDING");
        palletCode.setProductId(dto.getProductId());
        palletCode.setProductStatus(productStatus);
        palletCode.setProductionDate(dto.getProductionDate());
        palletCode.setScreenMeshId(screenMeshId);
        palletCode.setCurrentCycleNo(nextCycle);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(LocalDateTime.now());
        this.updateById(palletCode);

        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("半成品".equals(productStatus) ? "SEMI_BIND" : "FINISH_BIND");
        flow.setOperationName("半成品".equals(productStatus) ? "半成品入库登记" : "成品入库登记");
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(product.getId());
        flow.setProductStatus(productStatus);
        flow.setAssayId(null);
        flow.setCycleNo(nextCycle);
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
        rejectLegacyProductionFlowWrite();
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("明细不能为空");
        }
        PalletCode finishedPallet = parseAndFind(dto.getCode());
        PalletTask task = palletTaskMapper.selectPendingByCycle(
                finishedPallet.getId(),
                getCurrentCycleNo(finishedPallet)
        );
        if (task == null) {
            throw new BusinessException("未找到待处理的成品入库任务");
        }
        if (!"FINISH_IN".equals(task.getTaskType())) {
            throw new BusinessException("当前托盘待处理任务不是成品入库任务");
        }

        List<PalletTaskSemiItem> entities = new ArrayList<>();
        List<TaskSemiItemVO> voList = new ArrayList<>();
        Set<Long> seenBalanceIds = new LinkedHashSet<>();

        for (TaskSemiItemDTO itemDTO : dto.getItems()) {
            Long balanceId = itemDTO.getPrepareBalanceId();
            if (balanceId == null) {
            throw new BusinessException("请选择半成品历史批次");
            }
            if (!seenBalanceIds.add(balanceId)) {
                throw new BusinessException("同一请求中不允许重复选择同一半成品历史批次");
            }

            SemiPreparePoolBalance balance = semiPreparePoolBalanceMapper.selectById(balanceId);
            if (balance == null || safeInt(balance.getRemainingPieces()) <= 0) {
                throw new BusinessException("半成品历史批次不存在或已无可用余额");
            }
            Product semiProduct = productMapper.selectById(balance.getProductId());
            if (semiProduct == null) {
                throw new BusinessException("半成品不存在");
            }
            int totalPieces = calculateRegisterTotalPieces(itemDTO, semiProduct.getPiecesPerPallet());
            if (totalPieces > safeInt(balance.getRemainingPieces())) {
                throw new BusinessException("登记用量超过半成品历史批次剩余件数");
            }

            PalletTaskSemiItem entity = new PalletTaskSemiItem();
            entity.setPalletTaskId(task.getId());
            entity.setPrepareBalanceId(balance.getId());
            entity.setSemiProductId(balance.getProductId());
            entity.setProductionDate(balance.getProductionDate());
            entity.setQuantity(totalPieces);
            entity.setUnit("1");
            entity.setBoardCount(safeInt(itemDTO.getBoardCount()));
            entity.setPieceCount(safeInt(itemDTO.getPieceCount()));
            entity.setTotalPieces(totalPieces);
            entity.setUseAssay(Boolean.FALSE);
            entities.add(entity);

            TaskSemiItemVO vo = new TaskSemiItemVO();
            vo.setPrepareBalanceId(balance.getId());
            vo.setSemiProductId(balance.getProductId());
            vo.setSemiProductName(balance.getProductNameSnapshot());
            vo.setProductionDate(balance.getProductionDate());
            vo.setQuantity(totalPieces);
            vo.setUnit("1");
            vo.setBoardCount(entity.getBoardCount());
            vo.setPieceCount(entity.getPieceCount());
            vo.setTotalPieces(totalPieces);
            vo.setRemainingPieces(balance.getRemainingPieces());
            vo.setUseAssay(Boolean.FALSE);
            voList.add(vo);
        }

        palletTaskSemiItemMapper.deleteByTaskId(task.getId());
        for (int i = 0; i < entities.size(); i++) {
            PalletTaskSemiItem entity = entities.get(i);
            palletTaskSemiItemMapper.insert(entity);
            voList.get(i).setId(entity.getId());
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
    public PageResult<FixedProductQrPoolVO> pageFixedProductPool(FixedProductPoolQueryDTO queryDTO) {
        long pageNum = queryDTO.getPage() == null || queryDTO.getPage() <= 0 ? 1L : queryDTO.getPage();
        long pageSize = queryDTO.getSize() == null || queryDTO.getSize() <= 0 ? 10L : queryDTO.getSize();
        long offset = (pageNum - 1) * pageSize;
        List<FixedProductQrPoolVO> records = palletCodeQueryMapper.pageFixedProductPool(queryDTO, offset, pageSize);
        Long total = palletCodeQueryMapper.countFixedProductPool(queryDTO);
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
    public PageResult<PalletFlowCyclePageVO> pagePalletFlowCycles(String code, Long pageNum, Long pageSize) {
        PalletCode palletCode = parseAndFind(code);
        long safePageNum = pageNum == null || pageNum <= 0 ? 1L : pageNum;
        long safePageSize = pageSize == null || pageSize <= 0 ? 5L : pageSize;
        long offset = (safePageNum - 1) * safePageSize;
        List<PalletFlowCyclePageVO> records = palletFlowRecordMapper.pageFlowCycles(
                palletCode.getId(),
                offset,
                safePageSize
        );
        Long total = palletFlowRecordMapper.countFlowCycles(palletCode.getId());
        return new PageResult<>(total, records);
    }

    @Override
    public List<PalletFlowDetailVO> listPalletFlowsByCycle(String code, Integer cycleNo) {
        if (cycleNo == null) {
            throw new BusinessException("循环号不能为空");
        }
        PalletCode palletCode = parseAndFind(code);
        return palletFlowRecordMapper.listFlowDetails(palletCode.getId(), cycleNo);
    }

    @Override
    @Transactional
    public void deletePalletFlows(DeletePalletFlowBatchDTO dto) {
        if (dto == null || dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException("流转记录ID列表不能为空");
        }
        Set<Long> ids = dto.getIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new BusinessException("流转记录ID列表不能为空");
        }

        List<PalletFlowRecord> flows = palletFlowRecordMapper.selectBatchIds(ids);
        if (flows.size() != ids.size()) {
            throw new BusinessException("存在不存在的流转记录");
        }

        Set<Integer> palletCodeIds = flows.stream()
                .map(PalletFlowRecord::getPalletCodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, Integer> currentCycleNoByPallet = this.listByIds(palletCodeIds).stream()
                .collect(Collectors.toMap(PalletCode::getId, this::getCurrentCycleNo));
        if (currentCycleNoByPallet.size() != palletCodeIds.size()) {
            throw new BusinessException("存在异常的托盘码关联");
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(FLOW_RETENTION_DAYS);
        for (PalletFlowRecord flow : flows) {
            Integer flowCycleNo = flow.getCycleNo() == null ? 0 : flow.getCycleNo();
            Integer currentCycleNo = currentCycleNoByPallet.get(flow.getPalletCodeId());
            if (Objects.equals(flowCycleNo, currentCycleNo)) {
                throw new BusinessException("不允许删除当前轮次的流转记录");
            }
            if (flow.getOperationTime() == null || !flow.getOperationTime().isBefore(cutoff)) {
                throw new BusinessException("不允许删除180天内的流转记录");
            }
        }

        palletFlowRecordMapper.deleteBatchIds(ids);
    }

    @Override
    @Transactional
    public int cleanExpiredPalletFlows(int retentionDays) {
        if (retentionDays <= 0) {
            throw new BusinessException("清理保留天数必须大于0");
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int cleanableCount = palletFlowRecordMapper.countExpiredCleanableFlows(cutoff);
        if (cleanableCount <= 0) {
            return 0;
        }
        log.info("Found {} pallet flow records cleanable before {}", cleanableCount, cutoff);
        return palletFlowRecordMapper.deleteExpiredCleanableFlows(cutoff);
    }

    @Override
    @Transactional
    public InVO confirmSingleFinishedTaskIn(ConfirmPalletInItemDTO dto, Integer operatorId) {
        return confirmSingleFinishedTaskIn(dto, operatorId, null);
    }

    private InVO confirmSingleFinishedTaskIn(ConfirmPalletInItemDTO dto, Integer operatorId,
                                             Integer expectedPalletCodeId) {
        // 1) 解析托盘码并校验当前状态
        PalletCode palletCode = parseAndFindForUpdate(dto.getCode(), expectedPalletCodeId);
        if ("ORDER_RESERVED".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("该码是订单预打印标签，需先由生产管理确认生产结束后再入库");
        }
        if (!"PENDING".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("当前托盘状态不支持入库确认");
        }
        PalletTask task = findPendingInboundTask(palletCode);
        if (task == null) {
            throw new BusinessException("未找到待处理的入库任务");
        }
        ProductionOrderOutputCode productionOutputCode = productionOrderOutputCodeMapper.selectByTaskId(task.getId());
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
        String unit = productionOutputCode == null ? normalizeUnit(dto.getUnit()) : normalizeUnit(productionOutputCode.getUnit());
        int quantity = productionOutputCode == null ? normalizeQuantity(dto.getQuantity()) : normalizeQuantity(productionOutputCode.getQuantity());
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(dto.getWarehouseName());
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }
        if (!"0".equals(unit) && !"1".equals(unit)) {
            throw new BusinessException("单位仅支持0=板或1=件");
        }
        Product taskProduct = productMapper.selectById(task.getProductId());
        if (taskProduct == null) {
            throw new BusinessException("产品不存在");
        }
        validateSinglePalletOccupancy(unit, quantity, taskProduct.getPiecesPerPallet(), "二维码入库");
        // 4) 按任务类型分支处理
        InVO result;
        if ("SEMI_IN".equals(task.getTaskType())) {
            result = handleSemiInTask(dto.getRemark(), palletCode, task, warehouse, entryDate, side,
                    dto.getRowNumber(), dto.getLayer(), unit, quantity, operatorId);
        } else if ("FINISH_IN".equals(task.getTaskType())) {
            result = handleFinishInTask(dto.getRemark(), palletCode, task, warehouse, entryDate, side,
                    dto.getRowNumber(), dto.getLayer(), unit, quantity, operatorId);
        } else {
            throw new BusinessException("任务类型不支持入库确认");
        }
        Inventory inboundInventory = lambdaQueryInventoryByPalletId(palletCode.getId());
        productionOrderService.syncInboundByTask(task.getId(), inboundInventory == null ? null : inboundInventory.getId());
        return result;
    }

    @Override
    @Transactional
    public InVO createFixedProductInboundAndConfirm(BindPalletTaskDTO bindDTO,
                                                    ConfirmPalletInItemDTO confirmDTO,
                                                    Integer operatorId) {
        PalletCode palletCode = parseAndFindForUpdate(bindDTO.getCode());
        if (!Boolean.TRUE.equals(palletCode.getFixedModeEnabled()) || palletCode.getFixedProductId() == null) {
            throw new BusinessException("二维码未启用固定产品模式");
        }
        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 当前不是空闲状态");
        }
        if (!Objects.equals(palletCode.getFixedProductId(), bindDTO.getProductId())) {
            throw new BusinessException("固定产品二维码不能绑定到其他产品");
        }
        Product product = requireFixedProduct(palletCode.getFixedProductId());
        String productStatus = resolveInboundProductStatus(product.getStatus());
        validateSinglePalletOccupancy(normalizeUnit(bindDTO.getUnit()), normalizeQuantity(bindDTO.getQuantity()),
                product.getPiecesPerPallet(), "固定产品二维码入库");

        createInboundTaskForPallet(palletCode, product, productStatus, bindDTO.getProductionDate(),
                operatorId, bindDTO.getRemark());

        confirmDTO.setCode(palletCode.getCode());
        confirmDTO.setQuantity(bindDTO.getQuantity());
        confirmDTO.setUnit(bindDTO.getUnit());
        return confirmSingleFinishedTaskIn(confirmDTO, operatorId);
    }

    @Override
    @Transactional
    public List<InVO> confirmFinishedTaskInBatch(ConfirmPalletInBatchDTO dto, Integer operatorId) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("入库列表不能为空");
        }
        List<ResolvedInboundBatchItem> items = resolveAndSortInboundBatchItems(dto.getItems());
        // 预校验完成后按托盘主键固定顺序确认；任一项失败由外层事务整批回滚。
        List<InVO> results = new ArrayList<>(items.size());
        for (ResolvedInboundBatchItem item : items) {
            results.add(confirmSingleFinishedTaskIn(item.request(), operatorId, item.palletCodeId()));
        }
        return results;
    }

    @Override
    @Transactional
    public void createSemiOutTasks(CreateSemiOutTaskDTO dto, Integer operatorId) {
        createSemiOutTasks(dto.getCodes(), dto.getRemark(), operatorId, "DIRECT_OUT");
    }

    @Override
    @Transactional
    public void confirmSemiOutTasks(ConfirmSemiOutBatchDTO dto, Integer operatorId) {
        confirmSemiOutTasks(dto.getCodes(), dto.getRemark(), operatorId, "DIRECT_OUT");
    }

    @Override
    @Transactional
    public void createSemiPrepareTasks(CreateSemiPrepareTaskDTO dto, Integer operatorId) {
        rejectLegacyProductionFlowWrite();
        createSemiOutTasks(dto.getCodes(), dto.getRemark(), operatorId, "PREPARE_CONSUMED");
    }

    @Override
    @Transactional
    public void confirmSemiPrepareTasks(ConfirmSemiPrepareBatchDTO dto, Integer operatorId) {
        rejectLegacyProductionFlowWrite();
        confirmSemiOutTasks(dto.getCodes(), dto.getRemark(), operatorId, "PREPARE_CONSUMED");
    }

    @Override
    @Transactional
    public void confirmSemiConsume(ConfirmSemiConsumeBatchDTO dto, Integer operatorId) {
        rejectLegacyProductionFlowWrite();
        List<String> codes = normalizeAndValidateUniqueCodes(dto.getCodes());
        for (String code : codes) {
            PalletCode palletCode = parseAndFind(code);
            validateSemiPalletForOutFlow(palletCode);

            SemiPreparePool preparePool = findActivePreparePool(palletCode, true);
            if (preparePool == null) {
                throw new BusinessException("当前托盘不存在待消耗的历史生产占用记录");
            }
            if (lambdaQueryInventoryByPalletId(palletCode.getId(), true) != null) {
                throw new BusinessException("当前托盘仍在正常库存中，不能确认消耗");
            }

            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCode.getId());
            flow.setOperationType("CONSUMED");
            flow.setOperationName("半成品消耗");
            flow.setOperationTime(LocalDateTime.now());
            flow.setOperatorId(operatorId);
            flow.setProductId(palletCode.getProductId());
            flow.setProductStatus("半成品");
            flow.setAssayId(palletCode.getAssayId());
            flow.setCycleNo(getCurrentCycleNo(palletCode));
            flow.setRemark(buildPrepareConsumeRemark(palletCode, preparePool, dto.getRemark()));
            palletFlowRecordMapper.insert(flow);

            preparePool.setStatus("CONSUMED");
            preparePool.setUpdatedBy(operatorId);
            preparePool.setUpdatedAt(LocalDateTime.now());
            semiPreparePoolMapper.updateById(preparePool);

            releasePalletToFree(palletCode, operatorId);
        }
    }

    @Override
    @Transactional
    public void createFinishOutTasks(CreateFinishOutTaskDTO dto, Integer operatorId) {
        createOutTasks(dto.getCodes(), dto.getRemark(), operatorId, "FINISH_OUT", "成品");
    }

    @Override
    @Transactional
    public void confirmFinishOutTasks(ConfirmFinishOutBatchDTO dto, Integer operatorId) {
        confirmOutTasks(dto.getCodes(), dto.getRemark(), operatorId, "FINISH_OUT", "成品");
    }

    @Override
    @Transactional
    public void createTransferTasks(CreateTransferTaskDTO dto, Integer operatorId) {
        if (dto == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new BusinessException("调拨任务列表不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> seenCodes = new LinkedHashSet<>();
        for (CreateTransferTaskItemDTO item : dto.getItems()) {
            String code = normalizeCode(item.getCode());
            if (!seenCodes.add(code)) {
                throw new BusinessException("同一请求中不允许重复扫码同一托盘");
            }
            PalletCode palletCode = parseAndFind(code);
            validatePalletForTransfer(palletCode);
            Inventory inventory = requireInventoryByPallet(palletCode, false);
            Warehouse targetWarehouse = requireTargetWarehouse(item.getTargetWarehouseName());
            String targetSide = normalizeAndValidateSide(item.getTargetSide());

            int cycleNo = getCurrentCycleNo(palletCode);
            if (palletTaskMapper.countPendingOutTasks(palletCode.getId(), cycleNo) > 0) {
                throw new BusinessException("当前托盘已存在未完成的出库任务");
            }
            if (palletTaskMapper.countPendingTransferTasks(palletCode.getId(), cycleNo) > 0) {
                throw new BusinessException("当前托盘已存在未完成的调拨任务");
            }

            PalletTask task = new PalletTask();
            task.setPalletCodeId(palletCode.getId());
            task.setTaskType("TRANSFER");
            task.setBizScene(null);
            task.setStatus("PENDING");
            task.setProductId(palletCode.getProductId());
            task.setProductStatus(palletCode.getProductStatus());
            task.setProductionDate(palletCode.getProductionDate());
            task.setScreenMeshId(palletCode.getScreenMeshId());
            task.setAssayId(palletCode.getAssayId());
            task.setCreatedBy(operatorId);
            task.setCreatedAt(now);
            task.setRemark(item.getRemark());
            task.setCycleNo(cycleNo);
            task.setTargetWarehouseId(targetWarehouse.getId());
            task.setTargetSide(targetSide);
            palletTaskMapper.insert(task);
        }
    }

    @Override
    @Transactional
    public void confirmTransferTasks(ConfirmTransferBatchDTO dto, Integer operatorId) {
        List<String> codes = normalizeAndValidateUniqueCodes(dto.getCodes());
        for (String code : codes) {
            PalletCode palletCode = parseAndFindForUpdate(code);
            validatePalletForTransfer(palletCode);
            PalletTask task = requirePendingTransferTask(palletCode);
            Inventory inventory = requireInventoryByPallet(palletCode, true);
            Map<Integer, Warehouse> lockedWarehouses = lockWarehousesForTransfer(inventory.getWarehouseId(), task.getTargetWarehouseId());
            Warehouse targetWarehouse = lockedWarehouses.get(task.getTargetWarehouseId());
            if (targetWarehouse == null) {
                throw new BusinessException("目标仓库不存在");
            }
            String targetSide = normalizeAndValidateSide(task.getTargetSide());

            Product product = productMapper.selectById(palletCode.getProductId());
            if (product == null) {
                throw new BusinessException("产品不存在");
            }

            TransferSourceLocation sourceLocation = new TransferSourceLocation(
                    inventory.getWarehouseId(),
                    inventory.getSide(),
                    inventory.getRowNumber(),
                    inventory.getLayer()
            );
            TransferTargetLocation targetLocation = moveInventoryForTransferWithRetry(inventory, targetWarehouse, targetSide, product);
            insertTransferFlow(palletCode, task, sourceLocation, targetLocation, operatorId, dto.getRemark());
            recordPalletTransferEvent(
                    palletCode,
                    task,
                    inventory,
                    product,
                    sourceLocation,
                    targetLocation,
                    operatorId
            );
            touchPallet(palletCode, operatorId);
            confirmOutTask(task, operatorId, dto.getRemark());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TransferTaskPreviewVO previewTransferTasks(List<String> rawCodes) {
        List<String> codes = normalizeAndValidateUniqueCodes(rawCodes);
        List<TransferTaskPreviewVO.Item> items = new ArrayList<>();
        List<String> blockingIssues = new ArrayList<>();
        Map<Integer, TransferOccupancy> occupancyByWarehouse = new java.util.HashMap<>();

        for (String code : codes) {
            try {
                PalletCode palletCode = parseAndFind(code);
                validatePalletForTransfer(palletCode);
                PalletTask task = requirePendingTransferTask(palletCode);
                Inventory inventory = requireInventoryByPallet(palletCode, false);
                Warehouse sourceWarehouse = warehouseMapper.selectById(inventory.getWarehouseId());
                if (sourceWarehouse == null) {
                    throw new BusinessException("原仓库不存在");
                }
                Warehouse targetWarehouse = requireTargetWarehouse(task.getTargetWarehouseId());
                String targetSide = normalizeAndValidateSide(task.getTargetSide());
                Product product = productMapper.selectById(palletCode.getProductId());
                if (product == null) {
                    throw new BusinessException("产品不存在");
                }

                TransferOccupancy targetOccupancy = occupancyByWarehouse.computeIfAbsent(
                        targetWarehouse.getId(),
                        ignored -> loadTransferOccupancy(targetWarehouse)
                );
                TransferTargetLocation targetLocation = allocateTransferTargetLocation(
                        targetWarehouse, targetSide, product, targetOccupancy
                );
                if (Objects.equals(inventory.getWarehouseId(), targetLocation.warehouseId())
                        && Objects.equals(inventory.getSide(), targetLocation.side())
                        && Objects.equals(inventory.getRowNumber(), targetLocation.rowNumber())
                        && Objects.equals(inventory.getLayer(), targetLocation.layer())) {
                    throw new BusinessException("目标库位与当前库存位置相同，不能确认调拨");
                }

                TransferOccupancy sourceOccupancy = occupancyByWarehouse.computeIfAbsent(
                        sourceWarehouse.getId(),
                        ignored -> loadTransferOccupancy(sourceWarehouse)
                );
                targetOccupancy.reserve(targetLocation.side(), targetLocation.layer(), targetLocation.rowNumber());
                sourceOccupancy.release(inventory.getSide(), inventory.getLayer(), inventory.getRowNumber());

                boolean pieces = inventory.getPieces() != null && inventory.getPieces() > 0;
                items.add(TransferTaskPreviewVO.Item.builder()
                        .palletCode(code)
                        .currentWarehouseName(sourceWarehouse.getWarehouseName())
                        .currentSide(inventory.getSide())
                        .currentRowNumber(inventory.getRowNumber())
                        .currentLayer(inventory.getLayer())
                        .currentInventoryQuantity(pieces ? inventory.getPieces() : inventory.getQuantity())
                        .currentInventoryUnit(pieces ? "件" : "板")
                        .targetWarehouseName(targetWarehouse.getWarehouseName())
                        .targetSide(targetSide)
                        .plannedTargetRowNumber(targetLocation.rowNumber())
                        .plannedTargetLayer(targetLocation.layer())
                        .build());
            } catch (BusinessException e) {
                blockingIssues.add("托盘 " + code + " 当前不满足调拨确认条件：" + e.getMessage() + "。");
            }
        }

        return TransferTaskPreviewVO.builder()
                .items(List.copyOf(items))
                .blockingIssues(List.copyOf(blockingIssues))
                .build();
    }

    @Override
    @Transactional
    public WarehouseMapTaskCreateResultVO createWarehouseMapTasks(WarehouseMapBatchOperationDTO dto, Integer operatorId) {
        String operationType = dto.getOperationType() == null ? "" : dto.getOperationType().trim().toUpperCase();
        String side = normalizeAndValidateSide(dto.getSide());
        boolean hasExplicitCodes = dto.getCodes() != null && !dto.getCodes().isEmpty();
        int quantity = hasExplicitCodes ? dto.getCodes().size() : (dto.getQuantity() == null ? 0 : dto.getQuantity());
        if (quantity <= 0) {
            throw new BusinessException("操作数量必须大于0");
        }
        if (!"OUT".equals(operationType) && !"TRANSFER".equals(operationType) && !"PREPARE".equals(operationType)) {
            throw new BusinessException("当前仅支持平面图创建出库和调拨任务");
        }
        if ("PREPARE".equals(operationType)) {
            throw new BusinessException("旧流程入口已停用，半成品进入生产请通过生产订单领用");
        }
        Warehouse sourceWarehouse = requireTargetWarehouse(dto.getWarehouseId());
        List<Inventory> inventories = hasExplicitCodes
                ? resolveInventoriesByCodesForWarehouse(dto.getCodes(), sourceWarehouse.getId(), side, dto.getRowNumber(), dto.getLayer())
                : inventoryMapper.selectFrontPalletsForOperation(sourceWarehouse.getId(), side, quantity);
        if (inventories.size() < quantity) {
            throw new BusinessException("当前侧可操作托盘数量不足");
        }
        String batchNo = "WM" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String remark = appendTraceRemark(dto.getRemark(), BATCH_REMARK_PREFIX + "[" + batchNo + "]");
        LocalDateTime now = LocalDateTime.now();
        Warehouse targetWarehouse = null;
        String targetSide = null;
        if ("TRANSFER".equals(operationType)) {
            targetWarehouse = requireTargetWarehouse(dto.getTargetWarehouseName());
            targetSide = normalizeAndValidateSide(dto.getTargetSide());
        }

        WarehouseMapTaskCreateResultVO result = new WarehouseMapTaskCreateResultVO();
        result.setOperationBatchNo(batchNo);
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Inventory inventory : inventories) {
            PalletCode palletCode = this.getById(inventory.getPalletCodeId());
            if (palletCode == null) {
                throw new BusinessException("库存记录缺少有效托盘码");
            }
            if ("OUT".equals(operationType)) {
                createWarehouseMapOutTask(palletCode, operatorId, now, remark, batchNo);
                counts.merge(palletCode.getProductStatus() + "|OUT", 1, Integer::sum);
            } else if ("TRANSFER".equals(operationType)) {
                createWarehouseMapTransferTask(palletCode, operatorId, now, remark, batchNo, targetWarehouse, targetSide);
                counts.merge(palletCode.getProductStatus() + "|TRANSFER", 1, Integer::sum);
            } else {
                createWarehouseMapPrepareTask(palletCode, operatorId, now, remark, batchNo);
                counts.merge(palletCode.getProductStatus() + "|PREPARE", 1, Integer::sum);
            }
        }
        counts.forEach((key, count) -> addWarehouseMapResultItem(result, key, count));
        return result;
    }

    @Override
    @Transactional
    public InVO createWarehouseMapSlotInbound(WarehouseMapSlotInboundDTO dto, Integer operatorId) {
        String side = normalizeAndValidateSide(dto.getSide());
        Warehouse warehouse = requireTargetWarehouse(dto.getWarehouseName());
        if (dto.getRowNumber() < 1 || dto.getRowNumber() > warehouse.getMaxRows()) {
            throw new BusinessException("目标排号超出库位范围");
        }
        if (dto.getLayer() < 1 || dto.getLayer() > 2) {
            throw new BusinessException("目标层数非法");
        }
        List<Integer> usedRows = inventoryMapper.getUsedRowListForUpdate(warehouse.getId(), side, dto.getLayer());
        if (usedRows.contains(dto.getRowNumber())) {
            throw new BusinessException("目标位置已有库存，不能入库");
        }

        String locationRemark = "平面图单板入库目标位置：" + dto.getLayer() + "层 " + side + "侧 " + dto.getRowNumber() + "排";
        String remark = appendTraceRemark(dto.getRemark(), locationRemark);
        BindPalletTaskDTO bindDTO = new BindPalletTaskDTO();
        bindDTO.setCode(dto.getCode());
        bindDTO.setProductId(dto.getProductId());
        bindDTO.setProductStatus(dto.getProductStatus());
        bindDTO.setProductionDate(dto.getProductionDate());
        bindDTO.setQuantity(1);
        bindDTO.setUnit("0");
        bindDTO.setRemark(remark);
        bindPalletAndCreateTask(bindDTO, operatorId);

        ConfirmPalletInItemDTO confirmDTO = new ConfirmPalletInItemDTO();
        confirmDTO.setCode(dto.getCode());
        confirmDTO.setWarehouseName(dto.getWarehouseName());
        confirmDTO.setEntryDate(dto.getProductionDate());
        confirmDTO.setSide(side);
        confirmDTO.setRowNumber(dto.getRowNumber());
        confirmDTO.setLayer(dto.getLayer());
        confirmDTO.setQuantity(1);
        confirmDTO.setUnit("0");
        confirmDTO.setRemark(remark);
        return confirmSingleFinishedTaskIn(confirmDTO, operatorId);
    }

    private List<Inventory> resolveInventoriesByCodesForWarehouse(List<String> rawCodes, Integer warehouseId, String side,
                                                                  Integer rowNumber, Integer layer) {
        List<String> codes = normalizeAndValidateUniqueCodes(rawCodes);
        List<Inventory> inventories = new ArrayList<>(codes.size());
        for (String code : codes) {
            PalletCode palletCode = parseAndFind(code);
            Inventory inventory = requireInventoryByPallet(palletCode, false);
            if (!Objects.equals(inventory.getWarehouseId(), warehouseId) || !side.equals(inventory.getSide())) {
                throw new BusinessException("托盘[" + code + "]不在当前库位侧别中");
            }
            if (rowNumber != null && !Objects.equals(inventory.getRowNumber(), rowNumber)) {
                throw new BusinessException("托盘[" + code + "]不在当前指定排号中");
            }
            if (layer != null && !Objects.equals(inventory.getLayer(), layer)) {
                throw new BusinessException("托盘[" + code + "]不在当前指定层数中");
            }
            inventories.add(inventory);
        }
        return inventories;
    }

    private void createWarehouseMapOutTask(PalletCode palletCode, Integer operatorId, LocalDateTime now,
                                           String remark, String batchNo) {
        String productStatus = palletCode.getProductStatus();
        if (!"半成品".equals(productStatus) && !"成品".equals(productStatus)) {
            throw new BusinessException("仅支持在库的半成品或成品托盘出库");
        }
        String bizScene = "半成品".equals(productStatus) ? "DIRECT_OUT" : "FINISH_OUT";
        validatePalletForOutFlow(palletCode, productStatus);
        int cycleNo = getCurrentCycleNo(palletCode);
        if (lambdaQueryInventoryByPalletId(palletCode.getId()) == null) {
            throw new BusinessException("当前托盘不在正常库存中");
        }
        if ("半成品".equals(productStatus) && findActivePreparePool(palletCode, false) != null) {
            throw new BusinessException("当前托盘已存在激活中的历史生产占用记录");
        }
        if (palletTaskMapper.countPendingOutTasks(palletCode.getId(), cycleNo) > 0) {
            throw new BusinessException("当前托盘已存在未完成的出库任务");
        }
        PalletTask task = buildPendingTask(palletCode, "OUT", bizScene, operatorId, now, remark, cycleNo, batchNo);
        palletTaskMapper.insert(task);
    }

    private void createWarehouseMapTransferTask(PalletCode palletCode, Integer operatorId, LocalDateTime now,
                                                String remark, String batchNo, Warehouse targetWarehouse, String targetSide) {
        validatePalletForTransfer(palletCode);
        requireInventoryByPallet(palletCode, false);
        int cycleNo = getCurrentCycleNo(palletCode);
        if (palletTaskMapper.countPendingOutTasks(palletCode.getId(), cycleNo) > 0) {
            throw new BusinessException("当前托盘已存在未完成的出库任务");
        }
        if (palletTaskMapper.countPendingTransferTasks(palletCode.getId(), cycleNo) > 0) {
            throw new BusinessException("当前托盘已存在未完成的调拨任务");
        }
        PalletTask task = buildPendingTask(palletCode, "TRANSFER", null, operatorId, now, remark, cycleNo, batchNo);
        task.setTargetWarehouseId(targetWarehouse.getId());
        task.setTargetSide(targetSide);
        palletTaskMapper.insert(task);
    }

    private void createWarehouseMapPrepareTask(PalletCode palletCode, Integer operatorId, LocalDateTime now,
                                               String remark, String batchNo) {
        validatePalletForOutFlow(palletCode, "半成品");
        requireInventoryByPallet(palletCode, false);
        int cycleNo = getCurrentCycleNo(palletCode);
        if (findActivePreparePool(palletCode, false) != null) {
            throw new BusinessException("当前托盘已存在激活中的历史生产占用记录");
        }
        if (palletTaskMapper.countPendingOutTasks(palletCode.getId(), cycleNo) > 0) {
            throw new BusinessException("当前托盘已存在未完成的出库任务");
        }
        PalletTask task = buildPendingTask(palletCode, "OUT", "PREPARE_CONSUMED", operatorId, now, remark, cycleNo, batchNo);
        palletTaskMapper.insert(task);
    }

    private PalletTask buildPendingTask(PalletCode palletCode, String taskType, String bizScene, Integer operatorId,
                                        LocalDateTime now, String remark, int cycleNo, String batchNo) {
        PalletTask task = new PalletTask();
        task.setPalletCodeId(palletCode.getId());
        task.setTaskType(taskType);
        task.setBizScene(bizScene);
        task.setStatus("PENDING");
        task.setProductId(palletCode.getProductId());
        task.setProductStatus(palletCode.getProductStatus());
        task.setProductionDate(palletCode.getProductionDate());
        task.setScreenMeshId(palletCode.getScreenMeshId());
        task.setAssayId(palletCode.getAssayId());
        task.setCreatedBy(operatorId);
        task.setCreatedAt(now);
        task.setRemark(remark);
        task.setCycleNo(cycleNo);
        task.setOperationBatchNo(batchNo);
        return task;
    }

    private void addWarehouseMapResultItem(WarehouseMapTaskCreateResultVO result, String key, Integer count) {
        String[] parts = key.split("\\|", 2);
        String productStatus = parts[0];
        String taskType = parts.length > 1 ? parts[1] : "";
        WarehouseMapTaskCreateResultVO.Item item = new WarehouseMapTaskCreateResultVO.Item();
        item.setProductStatus(productStatus);
        item.setTaskType(taskType);
        item.setBizScene("PREPARE".equals(taskType) ? "PREPARE_CONSUMED" : ("OUT".equals(taskType) ? ("半成品".equals(productStatus) ? "DIRECT_OUT" : "FINISH_OUT") : null));
        item.setCount(count);
        if ("TRANSFER".equals(taskType)) {
            item.setRoutePath("/pallet-task/transfer");
        } else if ("PREPARE".equals(taskType)) {
            item.setRoutePath("/pallet-task/semi/out");
        } else if ("半成品".equals(productStatus)) {
            item.setRoutePath("/pallet-task/semi/out");
        } else {
            item.setRoutePath("/pallet-task/finish/out");
        }
        result.getItems().add(item);
    }

    private void createSemiOutTasks(List<String> rawCodes, String remark, Integer operatorId, String bizScene) {
        createOutTasks(rawCodes, remark, operatorId, bizScene, "半成品");
    }

    private void createOutTasks(List<String> rawCodes, String remark, Integer operatorId,
                                String bizScene, String productStatus) {
        List<String> codes = normalizeAndValidateUniqueCodes(rawCodes);
        LocalDateTime now = LocalDateTime.now();
        for (String code : codes) {
            PalletCode palletCode = parseAndFind(code);
            validatePalletForOutFlow(palletCode, productStatus);
            int cycleNo = getCurrentCycleNo(palletCode);

            if (lambdaQueryInventoryByPalletId(palletCode.getId()) == null) {
                throw new BusinessException("当前托盘不在正常库存中");
            }
            if ("半成品".equals(productStatus) && findActivePreparePool(palletCode, false) != null) {
                throw new BusinessException("当前托盘已存在激活中的历史生产占用记录");
            }
            if (palletTaskMapper.countPendingOutTasks(palletCode.getId(), cycleNo) > 0) {
                throw new BusinessException("当前托盘已存在未完成的出库任务");
            }

            PalletTask task = new PalletTask();
            task.setPalletCodeId(palletCode.getId());
            task.setTaskType("OUT");
            task.setBizScene(bizScene);
            task.setStatus("PENDING");
            task.setProductId(palletCode.getProductId());
            task.setProductStatus(palletCode.getProductStatus());
            task.setProductionDate(palletCode.getProductionDate());
            task.setScreenMeshId(palletCode.getScreenMeshId());
            task.setAssayId(palletCode.getAssayId());
            task.setCreatedBy(operatorId);
            task.setCreatedAt(now);
            task.setRemark(remark);
            task.setCycleNo(cycleNo);
            palletTaskMapper.insert(task);
        }
    }

    private void confirmSemiOutTasks(List<String> rawCodes, String remark, Integer operatorId, String bizScene) {
        confirmOutTasks(rawCodes, remark, operatorId, bizScene, "半成品");
    }

    private void confirmOutTasks(List<String> rawCodes, String remark, Integer operatorId,
                                 String bizScene, String productStatus) {
        List<String> codes = normalizeAndValidateUniqueCodes(rawCodes);
        for (String code : codes) {
            PalletCode palletCode = parseAndFind(code);
            validatePalletForOutFlow(palletCode, productStatus);

            PalletTask task = requirePendingOutTask(palletCode, bizScene);
            Inventory inventory = requireInventoryByPallet(palletCode, true);

            if ("DIRECT_OUT".equals(bizScene) && findActivePreparePool(palletCode, false) != null) {
                throw new BusinessException("当前托盘已处于历史生产占用流程中，不能再做普通出库");
            }

            OutStock outStock = executePalletLevelOutStock(palletCode, inventory, operatorId);
            recordPalletOutEvent(palletCode, task, inventory, outStock, operatorId, bizScene);

            if ("DIRECT_OUT".equals(bizScene)) {
                insertOutFlow(palletCode, task, inventory, operatorId, remark, "半成品出库");
                releasePalletToFree(palletCode, operatorId);
            } else if ("PREPARE_CONSUMED".equals(bizScene)) {
                if (findActivePreparePool(palletCode, true) != null) {
                    throw new BusinessException("当前托盘已存在激活中的历史生产占用记录");
                }
                PalletFlowRecord flow = insertPrepareOutFlow(palletCode, task, inventory, operatorId, remark);
                createProductionPrepareLedger(palletCode, task, inventory, flow, operatorId, remark);
                releasePalletToFree(palletCode, operatorId);
            } else if ("FINISH_OUT".equals(bizScene)) {
                insertOutFlow(palletCode, task, inventory, operatorId, remark, "成品出库");
                releasePalletToFree(palletCode, operatorId);
            } else {
                throw new BusinessException("不支持的出库业务场景");
            }

            confirmOutTask(task, operatorId, remark);
        }
    }

    private List<String> normalizeAndValidateUniqueCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            throw new BusinessException("托盘码列表不能为空");
        }
        List<String> normalized = new ArrayList<>(rawCodes.size());
        Set<String> seen = new LinkedHashSet<>();
        for (String rawCode : rawCodes) {
            String code = normalizeCode(rawCode);
            if (!seen.add(code)) {
                throw new BusinessException("同一请求中不允许重复扫码同一托盘");
            }
            normalized.add(code);
        }
        return normalized;
    }

    List<ResolvedInboundBatchItem> resolveAndSortInboundBatchItems(List<ConfirmPalletInItemDTO> rawItems) {
        validateTaskTransitionBatchSize(rawItems.size());
        List<ResolvedInboundBatchItem> resolved = new ArrayList<>(rawItems.size());
        Set<String> seenInputs = new LinkedHashSet<>();
        Set<Integer> seenPalletIds = new LinkedHashSet<>();
        for (ConfirmPalletInItemDTO item : rawItems) {
            if (item == null) {
                throw new BusinessException("入库项不能为空");
            }
            String code = normalizeCode(item.getCode());
            if (!seenInputs.add(code)) {
                throw new BusinessException("同一请求中不允许重复扫码同一托盘");
            }
            PalletCode palletCode = parseAndFind(code);
            if (palletCode.getId() == null) {
                throw new BusinessException("托盘数据异常");
            }
            if (!seenPalletIds.add(palletCode.getId())) {
                throw new BusinessException("同一请求中不允许重复扫码同一托盘");
            }
            item.setCode(code);
            resolved.add(new ResolvedInboundBatchItem(palletCode.getId(), item));
        }
        resolved.sort(Comparator.comparing(ResolvedInboundBatchItem::palletCodeId));
        return resolved;
    }

    private List<ResolvedPalletCode> resolveAndSortTaskCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            throw new BusinessException("托盘码列表不能为空");
        }
        validateTaskTransitionBatchSize(rawCodes.size());
        List<ResolvedPalletCode> resolved = new ArrayList<>(rawCodes.size());
        Set<String> seenInputs = new LinkedHashSet<>();
        Set<Integer> seenPalletIds = new LinkedHashSet<>();
        for (String rawCode : rawCodes) {
            String code = normalizeCode(rawCode);
            if (!seenInputs.add(code)) {
                throw new BusinessException("同一请求中不允许重复扫码同一托盘");
            }
            PalletCode palletCode = parseAndFind(code);
            if (palletCode.getId() == null) {
                throw new BusinessException("托盘数据异常");
            }
            if (!seenPalletIds.add(palletCode.getId())) {
                throw new BusinessException("同一请求中不允许重复扫码同一托盘");
            }
            resolved.add(new ResolvedPalletCode(palletCode.getId(), code));
        }
        resolved.sort(Comparator.comparing(ResolvedPalletCode::palletCodeId));
        return resolved;
    }

    private void validateTaskTransitionBatchSize(int size) {
        if (size > MAX_TASK_TRANSITION_BATCH_SIZE) {
            throw new BusinessException("单次最多处理20个托盘");
        }
    }

    record ResolvedInboundBatchItem(Integer palletCodeId, ConfirmPalletInItemDTO request) {
    }

    private record ResolvedPalletCode(Integer palletCodeId, String normalizedInput) {
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? null : rawCode.trim().toUpperCase();
        if (code == null || code.isBlank()) {
            throw new BusinessException("托盘码不能为空");
        }
        return code;
    }

    private byte[] renderFixedProductQrLabelPdf(List<PalletCode> palletCodes) {
        List<PalletQrLabelPdfRenderer.LabelPayload> labels = new ArrayList<>(palletCodes.size());
        for (PalletCode palletCode : palletCodes) {
            Product product = requireFixedProduct(palletCode.getFixedProductId());
            labels.add(new PalletQrLabelPdfRenderer.LabelPayload(palletCode.getCode(), product.getProductName()));
        }
        try {
            return PalletQrLabelPdfRenderer.renderA4LabelsWithTitle(labels);
        } catch (IOException e) {
            throw new BusinessException("生成固定产品二维码标签 PDF 失败");
        }
    }

    private Product requireFixedProduct(Integer productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("固定产品不存在");
        }
        return product;
    }

    private String resolveInboundProductStatus(String productStatus) {
        if ("半成品".equals(productStatus) || "成品".equals(productStatus)) {
            return productStatus;
        }
        throw new BusinessException("产品状态非法，无法启用固定产品二维码");
    }

    private PalletBindResultVO createInboundTaskForPallet(PalletCode palletCode,
                                                          Product product,
                                                          String productStatus,
                                                          LocalDate productionDate,
                                                          Integer operatorId,
                                                          String remark) {
        Integer screenMeshId = product.getScreenMeshId();

        int currentCycleNo = getCurrentCycleNo(palletCode);
        cancelPreviousCyclePendingTasks(palletCode, operatorId, "新轮次开始前自动收尾");
        long pending = palletTaskMapper.countPendingInTasks(palletCode.getId(), currentCycleNo);
        if (pending > 0) {
            throw new BusinessException("该托盘已存在未完成的入库任务");
        }

        int nextCycle = currentCycleNo + 1;
        String taskType;
        if ("半成品".equals(productStatus)) {
            taskType = "SEMI_IN";
        } else if ("成品".equals(productStatus)) {
            taskType = "FINISH_IN";
        } else {
            throw new BusinessException("产品状态非法");
        }

        LocalDateTime now = LocalDateTime.now();

        PalletTask task = new PalletTask();
        task.setPalletCodeId(palletCode.getId());
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setProductId(product.getId());
        task.setProductStatus(productStatus);
        task.setProductionDate(productionDate);
        task.setScreenMeshId(screenMeshId);
        task.setAssayId(null);
        task.setCreatedBy(operatorId);
        task.setCreatedAt(now);
        task.setRemark(remark);
        task.setCycleNo(nextCycle);
        palletTaskMapper.insert(task);

        palletCode.setStatus("PENDING");
        palletCode.setProductId(product.getId());
        palletCode.setProductStatus(productStatus);
        palletCode.setProductionDate(productionDate);
        palletCode.setScreenMeshId(screenMeshId);
        palletCode.setCurrentCycleNo(nextCycle);
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(now);
        this.updateById(palletCode);

        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("半成品".equals(productStatus) ? "SEMI_BIND" : "FINISH_BIND");
        flow.setOperationName("半成品".equals(productStatus) ? "半成品入库登记" : "成品入库登记");
        flow.setOperationTime(now);
        flow.setOperatorId(operatorId);
        flow.setProductId(product.getId());
        flow.setProductStatus(productStatus);
        flow.setAssayId(null);
        flow.setCycleNo(nextCycle);
        flow.setRemark(remark);
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
        vo.setProductionDate(productionDate);
        vo.setCreatedAt(task.getCreatedAt());
        vo.setRemark(remark);
        return vo;
    }

    private void validateFixedProductPrintable(PalletCode palletCode) {
        if (!Boolean.TRUE.equals(palletCode.getFixedModeEnabled()) || palletCode.getFixedProductId() == null) {
            throw new BusinessException("二维码未启用固定产品模式，不能在固定产品二维码池中打印");
        }
        if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("二维码 " + palletCode.getCode() + " 当前状态不是 FREE，不能重新打印");
        }
    }

    private void validateSemiPalletForOutFlow(PalletCode palletCode) {
        validatePalletForOutFlow(palletCode, "半成品");
    }

    private void validatePalletForOutFlow(PalletCode palletCode, String expectedProductStatus) {
        if (!"INSTOCK".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException(expectedProductStatus + "托盘当前不在可出库状态");
        }
        if (!expectedProductStatus.equals(palletCode.getProductStatus())) {
            throw new BusinessException("仅支持" + expectedProductStatus + "托盘操作");
        }
        if (palletCode.getProductId() == null || palletCode.getProductionDate() == null) {
            throw new BusinessException(expectedProductStatus + "托盘当前绑定信息不完整");
        }
    }

    private void validatePalletForTransfer(PalletCode palletCode) {
        if (!"INSTOCK".equalsIgnoreCase(palletCode.getStatus())) {
            throw new BusinessException("托盘当前不在可调拨状态");
        }
        if (!"半成品".equals(palletCode.getProductStatus()) && !"成品".equals(palletCode.getProductStatus())) {
            throw new BusinessException("仅支持在库的半成品或成品托盘调拨");
        }
        if (palletCode.getProductId() == null || palletCode.getProductionDate() == null) {
            throw new BusinessException("托盘当前绑定信息不完整");
        }
    }

    private PalletTask requirePendingOutTask(PalletCode palletCode, String bizScene) {
        PalletTask task = palletTaskMapper.selectPendingOutTaskByScene(
                palletCode.getId(),
                getCurrentCycleNo(palletCode),
                bizScene
        );
        if (task == null) {
            if ("DIRECT_OUT".equals(bizScene)) {
                throw new BusinessException("未找到待确认的半成品普通出库任务");
            }
            if ("PREPARE_CONSUMED".equals(bizScene)) {
                throw new BusinessException("未找到待确认的历史生产占用任务");
            }
            if ("FINISH_OUT".equals(bizScene)) {
                throw new BusinessException("未找到待确认的成品出库任务");
            }
            throw new BusinessException("未找到待确认的出库任务");
        }
        return task;
    }

    private PalletTask requirePendingTransferTask(PalletCode palletCode) {
        PalletTask task = palletTaskMapper.selectPendingTransferTaskByCycle(
                palletCode.getId(),
                getCurrentCycleNo(palletCode)
        );
        if (task == null) {
            throw new BusinessException("未找到待确认的调拨任务");
        }
        return task;
    }

    private Inventory requireInventoryByPallet(PalletCode palletCode, boolean forUpdate) {
        Inventory inventory = lambdaQueryInventoryByPalletId(palletCode.getId(), forUpdate);
        if (inventory == null) {
            throw new BusinessException("当前托盘不在正常库存中");
        }
        return inventory;
    }

    private Warehouse requireTargetWarehouse(String warehouseName) {
        if (warehouseName == null || warehouseName.isBlank()) {
            throw new BusinessException("目标仓库不能为空");
        }
        Warehouse warehouse = warehouseMapper.selectByWarehouseName(warehouseName);
        if (warehouse == null) {
            throw new BusinessException("目标仓库不存在");
        }
        return warehouse;
    }

    private Warehouse requireTargetWarehouse(Integer warehouseId) {
        if (warehouseId == null) {
            throw new BusinessException("调拨任务缺少目标仓库");
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException("目标仓库不存在");
        }
        return warehouse;
    }

    private String normalizeAndValidateSide(String side) {
        String normalized = normalizeSide(side);
        if (!LEFT_SIDE.equals(normalized) && !RIGHT_SIDE.equals(normalized)) {
            throw new BusinessException("侧向仅支持左或右");
        }
        return normalized;
    }

    private SemiPreparePool findActivePreparePool(PalletCode palletCode, boolean forUpdate) {
        int cycleNo = getCurrentCycleNo(palletCode);
        return forUpdate
                ? semiPreparePoolMapper.selectActiveByPalletAndCycleForUpdate(palletCode.getId(), cycleNo)
                : semiPreparePoolMapper.selectActiveByPalletAndCycle(palletCode.getId(), cycleNo);
    }

    private SemiPreparePool requireActivePreparePoolForBinding(PalletCode semiPallet) {
        SemiPreparePool preparePool = findActivePreparePool(semiPallet, false);
        if (preparePool == null) {
            throw new BusinessException("半成品托盘未进入历史生产占用流程，不能用于当前成品入库登记");
        }
        return preparePool;
    }

    private SemiPreparePool requireActivePreparePoolForConsumption(PalletCode semiPallet, boolean forUpdate) {
        SemiPreparePool preparePool = findActivePreparePool(semiPallet, forUpdate);
        if (preparePool == null) {
            throw new BusinessException("半成品托盘当前轮次不存在可消耗的历史生产占用记录");
        }
        return preparePool;
    }

    private TransferTargetLocation allocateTransferTargetLocation(Warehouse targetWarehouse, String targetSide, Product product) {
        int warehouseId = targetWarehouse.getId();
        List<Integer> leftUsedRowsLayer1 = inventoryMapper.getUsedRowListForUpdate(warehouseId, LEFT_SIDE, 1);
        List<Integer> rightUsedRowsLayer1 = inventoryMapper.getUsedRowListForUpdate(warehouseId, RIGHT_SIDE, 1);
        return allocateTransferTargetLocation(
                warehouseId,
                targetWarehouse.getMaxRows(),
                targetSide,
                product,
                leftUsedRowsLayer1,
                rightUsedRowsLayer1,
                () -> {
                    List<Integer> leftUsedRowsLayer2 = inventoryMapper.getUsedRowListForUpdate(warehouseId, LEFT_SIDE, 2);
                    List<Integer> rightUsedRowsLayer2 = inventoryMapper.getUsedRowListForUpdate(warehouseId, RIGHT_SIDE, 2);
                    return LEFT_SIDE.equals(targetSide) ? leftUsedRowsLayer2 : rightUsedRowsLayer2;
                }
        );
    }

    private TransferOccupancy loadTransferOccupancy(Warehouse warehouse) {
        if (warehouse.getMaxRows() == null || warehouse.getMaxRows() <= 0) {
            throw new BusinessException("目标仓库未配置有效排数");
        }
        int warehouseId = warehouse.getId();
        return new TransferOccupancy(
                warehouse.getMaxRows(),
                inventoryMapper.getUsedRowList(warehouseId, LEFT_SIDE, 1),
                inventoryMapper.getUsedRowList(warehouseId, RIGHT_SIDE, 1),
                inventoryMapper.getUsedRowList(warehouseId, LEFT_SIDE, 2),
                inventoryMapper.getUsedRowList(warehouseId, RIGHT_SIDE, 2)
        );
    }

    private TransferTargetLocation allocateTransferTargetLocation(Warehouse targetWarehouse, String targetSide,
                                                                    Product product, TransferOccupancy occupancy) {
        return allocateTransferTargetLocation(
                targetWarehouse.getId(),
                occupancy.maxRows(),
                targetSide,
                product,
                occupancy.usedRows(LEFT_SIDE, 1),
                occupancy.usedRows(RIGHT_SIDE, 1),
                () -> occupancy.usedRows(targetSide, 2)
        );
    }

    private TransferTargetLocation allocateTransferTargetLocation(int warehouseId, int maxRows, String targetSide,
                                                                    Product product,
                                                                    List<Integer> leftUsedRowsLayer1,
                                                                    List<Integer> rightUsedRowsLayer1,
                                                                    java.util.function.Supplier<List<Integer>> targetLayerTwoRows) {
        List<Integer> targetUnusedRowsLayer1 = getUnusedRowNumbers(
                LEFT_SIDE.equals(targetSide) ? leftUsedRowsLayer1 : rightUsedRowsLayer1,
                maxRows
        );

        boolean layerOneFull = leftUsedRowsLayer1.size() >= maxRows && rightUsedRowsLayer1.size() >= maxRows;
        if (!layerOneFull) {
            if (targetUnusedRowsLayer1.isEmpty()) {
                throw new BusinessException("目标仓库指定侧第一层已满，无法调拨");
            }
            return new TransferTargetLocation(warehouseId, targetSide, targetUnusedRowsLayer1.get(0), 1);
        }

        if (!Boolean.TRUE.equals(product.getCanStack())) {
            throw new BusinessException("目标仓库已满，无法调拨");
        }

        List<Integer> targetUnusedRowsLayer2 = getUnusedRowNumbers(
                targetLayerTwoRows.get(),
                maxRows
        );
        if (targetUnusedRowsLayer2.isEmpty()) {
            throw new BusinessException("目标仓库指定侧已满，无法调拨");
        }
        return new TransferTargetLocation(warehouseId, targetSide, targetUnusedRowsLayer2.get(0), 2);
    }

    private Map<Integer, Warehouse> lockWarehousesForTransfer(Integer sourceWarehouseId, Integer targetWarehouseId) {
        Set<Integer> warehouseIds = new java.util.TreeSet<>();
        if (sourceWarehouseId != null) {
            warehouseIds.add(sourceWarehouseId);
        }
        if (targetWarehouseId != null) {
            warehouseIds.add(targetWarehouseId);
        }
        Map<Integer, Warehouse> warehouses = new java.util.HashMap<>();
        for (Integer warehouseId : warehouseIds) {
            Warehouse warehouse = warehouseMapper.selectByIdForUpdate(warehouseId);
            if (warehouse == null) {
                throw new BusinessException("仓库不存在");
            }
            warehouses.put(warehouseId, warehouse);
        }
        return warehouses;
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

    private TransferTargetLocation moveInventoryForTransferWithRetry(Inventory inventory, Warehouse targetWarehouse,
                                                                     String targetSide, Product product) {
        for (int retry = 0; retry < MAX_LOCATION_RETRY; retry++) {
            TransferTargetLocation targetLocation = allocateTransferTargetLocation(targetWarehouse, targetSide, product);
            if (Objects.equals(inventory.getWarehouseId(), targetWarehouse.getId())
                    && Objects.equals(inventory.getSide(), targetLocation.side())
                    && Objects.equals(inventory.getRowNumber(), targetLocation.rowNumber())
                    && Objects.equals(inventory.getLayer(), targetLocation.layer())) {
                throw new BusinessException("目标库位与当前库存位置相同，不能确认调拨");
            }
            try {
                moveInventoryForTransfer(inventory, targetWarehouse, targetLocation);
                return targetLocation;
            } catch (DuplicateKeyException e) {
                if (retry == MAX_LOCATION_RETRY - 1) {
                    throw new BusinessException("库位分配冲突，请重试");
                }
            }
        }
        throw new BusinessException("库位分配冲突，请重试");
    }

    private void moveInventoryForTransfer(Inventory inventory, Warehouse targetWarehouse, TransferTargetLocation targetLocation) {
        Integer sourceWarehouseId = inventory.getWarehouseId();
        inventoryMapper.updateLocation(
                inventory.getId(),
                targetWarehouse.getId(),
                targetLocation.side(),
                targetLocation.rowNumber(),
                targetLocation.layer()
        );
        if (!Objects.equals(sourceWarehouseId, targetWarehouse.getId())) {
            Warehouse sourceWarehouse = warehouseMapper.selectById(sourceWarehouseId);
            if (sourceWarehouse == null) {
                throw new BusinessException("原仓库不存在");
            }
            warehouseMapper.updateCurCapacity(sourceWarehouse.getId(), sourceWarehouse.getCurCapacity() - 1);
            warehouseMapper.updateCurCapacity(targetWarehouse.getId(), targetWarehouse.getCurCapacity() + 1);
        }
        inventory.setWarehouseId(targetWarehouse.getId());
        inventory.setSide(targetLocation.side());
        inventory.setRowNumber(targetLocation.rowNumber());
        inventory.setLayer(targetLocation.layer());
    }

    private OutStock executePalletLevelOutStock(PalletCode palletCode, Inventory inventory, Integer operatorId) {
        Product product = productMapper.selectById(inventory.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        Warehouse warehouse = warehouseMapper.selectById(inventory.getWarehouseId());
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }

        int pieces = inventory.getPieces() != null ? inventory.getPieces() : 0;
        int quantity = (pieces > 0) ? 0 : (inventory.getQuantity() == null ? 1 : inventory.getQuantity());

        OutStock outStock = new OutStock();
        outStock.setWarehouseId(inventory.getWarehouseId());
        outStock.setProductId(inventory.getProductId());
        outStock.setQuantity(quantity);
        outStock.setPieces(pieces);
        outStock.setOutType(0);
        outStock.setUnit(pieces > 0 ? "1" : "0");
        outStock.setInDate(inventory.getEntryDate());
        outStock.setOutDate(LocalDate.now());
        outStock.setOperatorId(operatorId);
        outStock.setAssayId(inventory.getAssayId());
        outStock.setCreatedAt(LocalDateTime.now());
        outStock.setTotalWeight(buildPalletOutWeight(product, inventory));
        outStockMapper.insert(outStock);

        inventoryMapper.deleteInventoryById(inventory.getId());
        warehouseMapper.updateCurCapacity(warehouse.getId(), warehouse.getCurCapacity() - 1);
        return outStock;
    }

    private void recordPalletTransferEvent(PalletCode palletCode, PalletTask task, Inventory inventory,
                                           Product product, TransferSourceLocation sourceLocation,
                                           TransferTargetLocation targetLocation, Integer operatorId) {
        int loosePieces = inventory.getPieces() == null ? 0 : inventory.getPieces();
        int boards = loosePieces > 0 ? 0 : Math.max(1, inventory.getQuantity() == null ? 1 : inventory.getQuantity());
        int totalPieces = loosePieces > 0 ? loosePieces : boards * product.getPiecesPerPallet();
        stockMovementEventService.record(StockMovementEventCommand.builder()
                .eventType("TRANSFER")
                .sourceType("PALLET_TASK")
                .sourceRecordId(task.getId().longValue())
                .occurredAt(LocalDateTime.now())
                .productId(product.getId())
                .productStatus(inventory.getProductStatus())
                .productionDate(palletCode.getProductionDate() == null
                        ? inventory.getEntryDate()
                        : palletCode.getProductionDate())
                .fromWarehouseId(sourceLocation.warehouseId())
                .toWarehouseId(targetLocation.warehouseId())
                .palletCodeId(palletCode.getId())
                .boardQuantity(boards)
                .loosePieceQuantity(loosePieces)
                .totalPieces(totalPieces)
                .totalWeightKg(product.getWeightPerPiece().multiply(BigDecimal.valueOf(totalPieces)))
                .operatorId(operatorId)
                .actionKind("PALLET_TRANSFER")
                .businessActionId(preferredTaskActionId(task, "pallet_transfer"))
                .build());
    }

    private void recordPalletOutEvent(PalletCode palletCode, PalletTask task, Inventory inventory,
                                      OutStock outStock, Integer operatorId, String bizScene) {
        Product product = productMapper.selectById(inventory.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        int loosePieces = outStock.getPieces() == null ? 0 : outStock.getPieces();
        int boards = loosePieces > 0 ? 0 : Math.max(0, outStock.getQuantity());
        int totalPieces = loosePieces > 0 ? loosePieces : boards * product.getPiecesPerPallet();
        stockMovementEventService.record(StockMovementEventCommand.builder()
                .eventType("OUTBOUND")
                .sourceType("OUT_STOCK")
                .sourceRecordId(outStock.getId().longValue())
                .occurredAt(outStock.getCreatedAt())
                .productId(product.getId())
                .productStatus(inventory.getProductStatus())
                .productionDate(palletCode.getProductionDate() == null
                        ? inventory.getEntryDate()
                        : palletCode.getProductionDate())
                .fromWarehouseId(inventory.getWarehouseId())
                .palletCodeId(palletCode.getId())
                .boardQuantity(boards)
                .loosePieceQuantity(loosePieces)
                .totalPieces(totalPieces)
                .totalWeightKg(outStock.getTotalWeight())
                .operatorId(operatorId)
                .actionKind(bizScene)
                .businessActionId(preferredTaskActionId(task, "pallet_out"))
                .build());
    }

    private String preferredTaskActionId(PalletTask task, String prefix) {
        if (task.getOperationBatchNo() != null && !task.getOperationBatchNo().isBlank()) {
            return task.getOperationBatchNo();
        }
        return prefix + "_task_" + task.getId();
    }

    private BigDecimal buildPalletOutWeight(Product product, Inventory inventory) {
        int pieces = inventory.getPieces() != null && inventory.getPieces() > 0
                ? inventory.getPieces()
                : (inventory.getQuantity() == null ? 1 : inventory.getQuantity()) * product.getPiecesPerPallet();
        return product.getWeightPerPiece().multiply(BigDecimal.valueOf(pieces));
    }

    private void insertOutFlow(PalletCode palletCode, PalletTask task, Inventory inventory,
                               Integer operatorId, String remark, String operationName) {
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("OUT");
        flow.setOperationName(operationName);
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus(palletCode.getProductStatus());
        flow.setAssayId(palletCode.getAssayId());
        flow.setFromWarehouseId(inventory.getWarehouseId());
        flow.setFromSide(inventory.getSide());
        flow.setFromRowNumber(inventory.getRowNumber());
        flow.setFromLayer(inventory.getLayer());
        flow.setCycleNo(task.getCycleNo());
        flow.setRemark(appendTraceRemark(remark, "托盘[" + palletCode.getCode() + "]" + operationName + "，任务ID=" + task.getId()));
        palletFlowRecordMapper.insert(flow);
    }

    private void insertTransferFlow(PalletCode palletCode, PalletTask task, TransferSourceLocation sourceLocation,
                                    TransferTargetLocation targetLocation, Integer operatorId, String remark) {
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("TRANSFER");
        flow.setOperationName("托盘调拨");
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus(palletCode.getProductStatus());
        flow.setAssayId(palletCode.getAssayId());
        flow.setFromWarehouseId(sourceLocation.warehouseId());
        flow.setFromSide(sourceLocation.side());
        flow.setFromRowNumber(sourceLocation.rowNumber());
        flow.setFromLayer(sourceLocation.layer());
        flow.setToWarehouseId(targetLocation.warehouseId());
        flow.setToSide(targetLocation.side());
        flow.setToRowNumber(targetLocation.rowNumber());
        flow.setToLayer(targetLocation.layer());
        flow.setCycleNo(task.getCycleNo());
        flow.setRemark(appendTraceRemark(remark, "托盘[" + palletCode.getCode() + "]调拨，任务ID=" + task.getId()));
        palletFlowRecordMapper.insert(flow);
    }

    private void insertPreparePoolFlow(PalletCode palletCode, PalletTask task, Inventory inventory,
                                       Integer operatorId, String remark) {
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("PREPARE_CONSUMED");
        flow.setOperationName("历史生产占用");
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus("半成品");
        flow.setAssayId(palletCode.getAssayId());
        flow.setFromWarehouseId(inventory.getWarehouseId());
        flow.setFromSide(inventory.getSide());
        flow.setFromRowNumber(inventory.getRowNumber());
        flow.setFromLayer(inventory.getLayer());
        flow.setCycleNo(task.getCycleNo());
        flow.setRemark(appendTraceRemark(
                firstNonBlank(task.getRemark(), remark),
                "托盘[" + palletCode.getCode() + "]历史生产占用，任务ID=" + task.getId()
        ));
        palletFlowRecordMapper.insert(flow);
    }

    private PalletFlowRecord insertPrepareOutFlow(PalletCode palletCode, PalletTask task, Inventory inventory,
                                                  Integer operatorId, String remark) {
        Product product = productMapper.selectById(palletCode.getProductId());
        ProductionPrepareLedgerSnapshot snapshot;
        try {
            snapshot = ProductionPrepareLedgerSnapshotCalculator.calculate(inventory, product);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("PREPARE_CONSUMED");
        flow.setOperationName("历史生产占用");
        flow.setOperationTime(LocalDateTime.now());
        flow.setOperatorId(operatorId);
        flow.setProductId(palletCode.getProductId());
        flow.setProductStatus("半成品");
        flow.setAssayId(palletCode.getAssayId());
        flow.setFromWarehouseId(inventory.getWarehouseId());
        flow.setFromSide(inventory.getSide());
        flow.setFromRowNumber(inventory.getRowNumber());
        flow.setFromLayer(inventory.getLayer());
        flow.setCycleNo(task.getCycleNo());
        flow.setRemark(buildPrepareOutRemark(palletCode, task, inventory, snapshot, remark));
        palletFlowRecordMapper.insert(flow);
        return flow;
    }

    private void createProductionPrepareLedger(PalletCode palletCode, PalletTask task, Inventory inventory,
                                               PalletFlowRecord flow, Integer operatorId, String remark) {
        Product product = productMapper.selectById(palletCode.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在，无法写入历史生产占用台账");
        }
        ProductionPrepareLedgerSnapshot snapshot;
        try {
            snapshot = ProductionPrepareLedgerSnapshotCalculator.calculate(inventory, product);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        Warehouse warehouse = inventory.getWarehouseId() == null ? null : warehouseMapper.selectById(inventory.getWarehouseId());

        ProductionPrepareLedger ledger = new ProductionPrepareLedger();
        ledger.setProductId(palletCode.getProductId());
        ledger.setProductNameSnapshot(product.getProductName());
        ledger.setProductStatus("半成品");
        ledger.setProductionDate(palletCode.getProductionDate());
        ledger.setScreenMeshId(palletCode.getScreenMeshId());
        ledger.setAssayId(palletCode.getAssayId());
        ledger.setSourcePalletCodeId(palletCode.getId());
        ledger.setSourcePalletCode(palletCode.getCode());
        ledger.setSourceInventoryId(inventory.getId());
        ledger.setSourceWarehouseId(inventory.getWarehouseId());
        ledger.setSourceWarehouseName(warehouse == null ? null : warehouse.getWarehouseName());
        ledger.setSourceSide(inventory.getSide());
        ledger.setSourceRowNumber(inventory.getRowNumber());
        ledger.setSourceLayer(inventory.getLayer());
        ledger.setBoardCountSnapshot(snapshot.boardCountSnapshot());
        ledger.setPieceCountSnapshot(snapshot.pieceCountSnapshot());
        ledger.setTotalPieces(snapshot.totalPieces());
        ledger.setPiecesPerPallet(snapshot.piecesPerPallet());
        ledger.setSourceTaskId(task.getId());
        ledger.setSourceFlowId(flow == null ? null : flow.getId());
        ledger.setCreatedBy(operatorId);
        ledger.setCreatedAt(LocalDateTime.now());
        ledger.setRemark(appendTraceRemark(firstNonBlank(task.getRemark(), remark),
                "已视为历史生产占用出库，二维码已释放"));
        productionPrepareLedgerMapper.insert(ledger);

        upsertPreparePoolBalance(palletCode, product, snapshot);
    }

    private String buildPrepareOutRemark(PalletCode palletCode, PalletTask task, Inventory inventory,
                                         ProductionPrepareLedgerSnapshot snapshot, String remark) {
        String sourceLocation = "来源库位ID=" + inventory.getWarehouseId()
                + "，侧别=" + firstNonBlank(inventory.getSide(), "-")
                + "，排=" + (inventory.getRowNumber() == null ? "-" : inventory.getRowNumber())
                + "，层=" + (inventory.getLayer() == null ? "-" : inventory.getLayer());
        String trace = "托盘[" + palletCode.getCode() + "]历史生产占用，任务ID=" + task.getId()
                + "；已视为仓库出库，二维码已释放"
                + "；折算数量：" + snapshot.totalPieces() + "件"
                + "；" + sourceLocation;
        return appendTraceRemark(firstNonBlank(task.getRemark(), remark), trace);
    }

    private void upsertPreparePoolBalance(PalletCode palletCode, Product product,
                                          ProductionPrepareLedgerSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        SemiPreparePoolBalance balance = semiPreparePoolBalanceMapper.selectByBatchForUpdate(
                palletCode.getProductId(),
                palletCode.getProductionDate(),
                palletCode.getScreenMeshId(),
                palletCode.getAssayId()
        );
        BigDecimal weightPerPiece = product.getWeightPerPiece() == null ? BigDecimal.ZERO : product.getWeightPerPiece();
        if (balance == null) {
            balance = new SemiPreparePoolBalance();
            balance.setProductId(palletCode.getProductId());
            balance.setProductNameSnapshot(product.getProductName());
            balance.setProductionDate(palletCode.getProductionDate());
            balance.setScreenMeshId(palletCode.getScreenMeshId());
            balance.setAssayId(palletCode.getAssayId());
            balance.setInPieces(snapshot.totalPieces());
            balance.setConsumedPieces(0);
            balance.setRemainingPieces(snapshot.totalPieces());
            balance.setPiecesPerPallet(snapshot.piecesPerPallet());
            balance.setWeightPerPiece(weightPerPiece);
            balance.setRemainingWeight(weightPerPiece.multiply(BigDecimal.valueOf(snapshot.totalPieces())));
            balance.setStatus("ACTIVE");
            balance.setFirstInAt(now);
            balance.setLastInAt(now);
            balance.setCreatedAt(now);
            balance.setUpdatedAt(now);
            semiPreparePoolBalanceMapper.insert(balance);
            return;
        }
        int inPieces = safeInt(balance.getInPieces()) + snapshot.totalPieces();
        int remainingPieces = safeInt(balance.getRemainingPieces()) + snapshot.totalPieces();
        balance.setProductNameSnapshot(product.getProductName());
        balance.setInPieces(inPieces);
        balance.setRemainingPieces(remainingPieces);
        balance.setPiecesPerPallet(snapshot.piecesPerPallet());
        balance.setWeightPerPiece(weightPerPiece);
        balance.setRemainingWeight(weightPerPiece.multiply(BigDecimal.valueOf(remainingPieces)));
        balance.setStatus("ACTIVE");
        balance.setLastInAt(now);
        balance.setUpdatedAt(now);
        semiPreparePoolBalanceMapper.updateById(balance);
    }

    private void createSemiPreparePoolRecord(PalletCode palletCode, PalletTask task, Integer operatorId, String remark) {
        LocalDateTime now = LocalDateTime.now();
        SemiPreparePool preparePool = new SemiPreparePool();
        preparePool.setProductId(palletCode.getProductId());
        preparePool.setProductionDate(palletCode.getProductionDate());
        preparePool.setPalletCodeId(palletCode.getId());
        preparePool.setCycleNo(task.getCycleNo());
        preparePool.setStatus("ACTIVE");
        preparePool.setRemark(firstNonBlank(task.getRemark(), remark));
        preparePool.setCreatedBy(operatorId);
        preparePool.setCreatedAt(now);
        preparePool.setUpdatedBy(operatorId);
        preparePool.setUpdatedAt(now);
        semiPreparePoolMapper.insert(preparePool);
    }

    private void confirmOutTask(PalletTask task, Integer operatorId, String remark) {
        task.setStatus("CONFIRMED");
        task.setConfirmedBy(operatorId);
        task.setConfirmedAt(LocalDateTime.now());
        task.setRemark(mergeRemarks(task.getRemark(), remark));
        palletTaskMapper.updateById(task);
    }

    private void touchPallet(PalletCode palletCode, Integer operatorId) {
        palletCode.setUpdatedBy(operatorId);
        palletCode.setUpdatedAt(LocalDateTime.now());
        this.updateById(palletCode);
    }

    private String buildPrepareConsumeRemark(PalletCode palletCode, SemiPreparePool preparePool, String remark) {
        String trace = "托盘[" + palletCode.getCode() + "]历史生产占用确认消耗";
        if (preparePool.getRemark() != null && !preparePool.getRemark().isBlank()) {
            trace = trace + "，历史占用备注=" + preparePool.getRemark();
        }
        return appendTraceRemark(remark, trace);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String mergeRemarks(String left, String right) {
        if (left == null || left.isBlank()) {
            return right;
        }
        if (right == null || right.isBlank()) {
            return left;
        }
        if (left.equals(right)) {
            return left;
        }
        return left + "；" + right;
    }

    private String appendTraceRemark(String remark, String traceRemark) {
        if (remark == null || remark.isBlank()) {
            return traceRemark;
        }
        return remark + "；" + traceRemark;
    }

    private static final class TransferSourceLocation {
        private final Integer warehouseId;
        private final String side;
        private final Integer rowNumber;
        private final Integer layer;

        private TransferSourceLocation(Integer warehouseId, String side, Integer rowNumber, Integer layer) {
            this.warehouseId = warehouseId;
            this.side = side;
            this.rowNumber = rowNumber;
            this.layer = layer;
        }

        private Integer warehouseId() {
            return warehouseId;
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

    private static final class TransferOccupancy {
        private final int maxRows;
        private final Set<Integer> leftLayerOne;
        private final Set<Integer> rightLayerOne;
        private final Set<Integer> leftLayerTwo;
        private final Set<Integer> rightLayerTwo;

        private TransferOccupancy(int maxRows,
                                  List<Integer> leftLayerOne,
                                  List<Integer> rightLayerOne,
                                  List<Integer> leftLayerTwo,
                                  List<Integer> rightLayerTwo) {
            this.maxRows = maxRows;
            this.leftLayerOne = new LinkedHashSet<>(leftLayerOne == null ? List.of() : leftLayerOne);
            this.rightLayerOne = new LinkedHashSet<>(rightLayerOne == null ? List.of() : rightLayerOne);
            this.leftLayerTwo = new LinkedHashSet<>(leftLayerTwo == null ? List.of() : leftLayerTwo);
            this.rightLayerTwo = new LinkedHashSet<>(rightLayerTwo == null ? List.of() : rightLayerTwo);
        }

        private int maxRows() {
            return maxRows;
        }

        private List<Integer> usedRows(String side, int layer) {
            return new ArrayList<>(rows(side, layer));
        }

        private void reserve(String side, int layer, Integer rowNumber) {
            rows(side, layer).add(rowNumber);
        }

        private void release(String side, Integer layer, Integer rowNumber) {
            if (side == null || layer == null || rowNumber == null) {
                return;
            }
            rows(side, layer).remove(rowNumber);
        }

        private Set<Integer> rows(String side, int layer) {
            boolean left = LEFT_SIDE.equals(side);
            if (layer == 1) {
                return left ? leftLayerOne : rightLayerOne;
            }
            return left ? leftLayerTwo : rightLayerTwo;
        }
    }

    private static final class TransferTargetLocation {
        private final Integer warehouseId;
        private final String side;
        private final Integer rowNumber;
        private final Integer layer;

        private TransferTargetLocation(Integer warehouseId, String side, Integer rowNumber, Integer layer) {
            this.warehouseId = warehouseId;
            this.side = side;
            this.rowNumber = rowNumber;
            this.layer = layer;
        }

        private Integer warehouseId() {
            return warehouseId;
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

    private InVO handleSemiInTask(String remark, PalletCode palletCode, PalletTask task,
                                  Warehouse warehouse, LocalDate entryDate,
                                  String side, Integer rowNumber, Integer layer,
                                  String unit, int quantity, Integer operatorId) {
        // 半成品任务：组装 AddSemiProductRecordDTO，走半成品入库链路
        Product product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        Integer assayId = ensureAssayId(palletCode, task, entryDate, operatorId, true);
        AddSemiProductRecordDTO recordDTO = new AddSemiProductRecordDTO();
        recordDTO.setProductId(task.getProductId());
        recordDTO.setWarehouseName(warehouse.getWarehouseName());
        recordDTO.setEntryDate(entryDate);
        recordDTO.setQuantity(quantity);
        recordDTO.setUnit(unit);
        recordDTO.setSide(side);
        recordDTO.setRowNumber(rowNumber);
        recordDTO.setLayer(layer);
        recordDTO.setScreenMeshId(task.getScreenMeshId() != null ? task.getScreenMeshId() : product.getScreenMeshId());
        recordDTO.setPalletCodeId(palletCode.getId());
        String operatorName = getOperatorName(operatorId);
        InVO result = semiProductRecordService.addSemiProductRecord(recordDTO, operatorName);
        // 入库成功后同步托盘/任务/流转记录
        finalizeTask(palletCode, task, assayId, warehouse, side, rowNumber, layer, remark, operatorId);
        return result;
    }

    private InVO handleFinishInTask(String remark, PalletCode palletCode, PalletTask task,
                                    Warehouse warehouse, LocalDate entryDate,
                                    String side, Integer rowNumber, Integer layer,
                                    String unit, int quantity, Integer operatorId) {
        // 成品任务：读取半成品明细、校验 useAssay 唯一，调用 stockIn
        Product product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new BusinessException("产品不存在");
        }
        List<SemiRecordDTO> semiRecords = buildSemiRecords(task);
        validateUseAssayCount(semiRecords);
        Integer assayId = ensureAssayId(palletCode, task, entryDate, operatorId, true);
        InStockRequestDTO requestDTO = new InStockRequestDTO();
        requestDTO.setProductId(task.getProductId());
        requestDTO.setWarehouseName(warehouse.getWarehouseName());
        requestDTO.setEntryDate(entryDate);
        requestDTO.setQuantity(quantity);
        requestDTO.setUnit(unit);
        requestDTO.setSide(side);
        requestDTO.setRowNumber(rowNumber);
        requestDTO.setLayer(layer);
        requestDTO.setScreenMeshId(task.getScreenMeshId() != null ? task.getScreenMeshId() : product.getScreenMeshId());
        requestDTO.setReturnInStockFlag("0");
        requestDTO.setSemiRecords(semiRecords == null ? new ArrayList<>() : semiRecords);
        requestDTO.setPalletCodeId(palletCode.getId());
        InVO result = inStockService.stockIn(requestDTO, operatorId);
        // 入库成功后先收尾成品托盘/任务，再处理已登记的半成品历史批次余额。
        finalizeTask(palletCode, task, assayId, warehouse, side, rowNumber, layer, remark, operatorId);
        consumePrepareBalancesAfterFinishIn(task, palletCode.getCode(), operatorId, remark);
        return result;
    }

    private void consumePrepareBalancesAfterFinishIn(PalletTask finishTask, String finishPalletCode,
                                                     Integer operatorId, String remark) {
        List<PalletTaskSemiItem> semiItems = palletTaskSemiItemMapper.selectList(
                new LambdaQueryWrapper<PalletTaskSemiItem>()
                        .eq(PalletTaskSemiItem::getPalletTaskId, finishTask.getId())
        );
        if (semiItems == null || semiItems.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (PalletTaskSemiItem item : semiItems) {
            if (item.getPrepareBalanceId() == null) {
                continue;
            }
            int consumePieces = item.getTotalPieces() != null ? item.getTotalPieces() : safeInt(item.getQuantity());
            if (consumePieces <= 0) {
                continue;
            }
            SemiPreparePoolBalance balance = semiPreparePoolBalanceMapper.selectByIdForUpdate(item.getPrepareBalanceId());
            if (balance == null) {
                throw new BusinessException("半成品历史批次不存在");
            }
            int remaining = safeInt(balance.getRemainingPieces());
            if (remaining < consumePieces) {
                throw new BusinessException("半成品历史批次余额不足，无法完成成品入库");
            }
            int newRemaining = remaining - consumePieces;
            balance.setConsumedPieces(safeInt(balance.getConsumedPieces()) + consumePieces);
            balance.setRemainingPieces(newRemaining);
            balance.setRemainingWeight(resolveRemainingWeight(balance, newRemaining));
            balance.setStatus(newRemaining == 0 ? "CONSUMED" : "ACTIVE");
            balance.setLastConsumedAt(now);
            balance.setUpdatedAt(now);
            semiPreparePoolBalanceMapper.updateById(balance);
            insertManualConsumptionRecord(finishTask, finishPalletCode, balance, consumePieces, operatorId, now, remark);
        }
    }

    private void rejectLegacyProductionFlowWrite() {
        throw new BusinessException("旧流程入口已停用，成品与半成品追溯请通过生产订单关联");
    }

    private String buildSemiConsumedRemark(PalletTask finishTask, String finishPalletCode, String remark) {
        String traceRemark = "成品托盘[" + finishPalletCode + "]确认入库后消耗，任务ID=" + finishTask.getId();
        if (remark == null || remark.isBlank()) {
            return traceRemark;
        }
        return remark + "；" + traceRemark;
    }

    private void insertManualConsumptionRecord(PalletTask finishTask, String finishPalletCode,
                                               SemiPreparePoolBalance balance, int consumePieces,
                                               Integer operatorId, LocalDateTime now, String remark) {
        ProductionConsumptionRecord record = new ProductionConsumptionRecord();
        record.setProductId(balance.getProductId());
        record.setProductNameSnapshot(balance.getProductNameSnapshot());
        record.setProductionDate(balance.getProductionDate());
        record.setScreenMeshId(balance.getScreenMeshId());
        record.setAssayId(balance.getAssayId());
        record.setConsumePieces(consumePieces);
        record.setBalanceId(balance.getId());
        record.setSourceTaskId(String.valueOf(finishTask.getId()));
        record.setSourceText(finishPalletCode);
        record.setCreatedBy(operatorId);
        record.setCreatedAt(now);
        record.setRemark(buildSemiConsumedRemark(finishTask, finishPalletCode, remark));
        productionConsumptionRecordMapper.insert(record);
    }

    private List<SemiRecordDTO> buildSemiRecords(PalletTask task) {
        List<PalletTaskSemiItem> semiItems = palletTaskSemiItemMapper.selectList(
                new LambdaQueryWrapper<PalletTaskSemiItem>()
                        .eq(PalletTaskSemiItem::getPalletTaskId, task.getId())
        );
        if (semiItems == null || semiItems.isEmpty()) {
            return new ArrayList<>();
        }
        List<SemiRecordDTO> semiRecords = new ArrayList<>();
        for (PalletTaskSemiItem item : semiItems) {
            SemiPreparePoolBalance balance = item.getPrepareBalanceId() == null ? null : semiPreparePoolBalanceMapper.selectById(item.getPrepareBalanceId());
            if (balance == null) {
                throw new BusinessException("半成品历史批次不存在");
            }
            SemiRecordDTO record = new SemiRecordDTO();
            record.setSemiProductId(balance.getProductId());
            record.setProductName(balance.getProductNameSnapshot());
            record.setProductionDate(balance.getProductionDate());
            record.setQuantity(item.getTotalPieces() != null ? item.getTotalPieces() : item.getQuantity());
            record.setUnit("1");
            record.setUseAssay(Boolean.FALSE);
            record.setFromPreparePool(Boolean.TRUE);
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
                              String side, Integer rowNumber, Integer layer, String remark, Integer operatorId) {
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
        task.setTargetWarehouseId(warehouse.getId());
        task.setTargetSide(side);
        task.setStatus("CONFIRMED");
        task.setConfirmedBy(operatorId);
        task.setConfirmedAt(now);
        palletTaskMapper.updateById(task);
        updateLatestFlowAssay(palletCode.getId(), assayId, task.getCycleNo());
        PalletFlowRecord flow = new PalletFlowRecord();
        flow.setPalletCodeId(palletCode.getId());
        flow.setTaskId(task.getId());
        flow.setOperationType("SEMI_IN".equals(task.getTaskType()) ? "SEMI_INSTOCK" : "FINISH_INSTOCK");
        flow.setOperationName("SEMI_IN".equals(task.getTaskType()) ? "半成品入库" : "成品入库");
        flow.setOperationTime(now);
        flow.setOperatorId(operatorId);
        flow.setProductId(task.getProductId());
        flow.setProductStatus(task.getProductStatus());
        flow.setAssayId(assayId);
        flow.setToWarehouseId(warehouse.getId());
        flow.setToSide(side);
        flow.setToRowNumber(rowNumber);
        flow.setToLayer(layer);
        flow.setCycleNo(task.getCycleNo());
        flow.setRemark(remark);
        palletFlowRecordMapper.insert(flow);
    }

    private PalletTask findPendingInboundTask(PalletCode palletCode) {
        return palletTaskMapper.selectPendingByCycle(
                palletCode.getId(),
                getCurrentCycleNo(palletCode)
        );
    }

    private String getOperatorName(Integer operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作人不存在");
        }
        return operator.getName();
    }

    private Integer ensureAssayId(PalletCode palletCode, PalletTask task, LocalDate entryDate,
                                  Integer operatorId, boolean writeBack) {
        Inventory inventory = lambdaQueryInventoryByPalletId(palletCode.getId());
        AssayResolveResult result = assayResolveService.resolveForPallet(
                palletCode,
                task,
                inventory,
                entryDate,
                operatorId,
                writeBack
        );
        return result.hasAssay() ? result.getAssay().getId() : null;
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

    private void validateSinglePalletOccupancy(String unit, Integer quantity, Integer piecesPerPallet, String scene) {
        PalletInventoryOccupancyRule.validateSingleQrInventory(unit, normalizeQuantity(quantity), piecesPerPallet, scene);
    }

    @Override
    @Transactional
    public void invalidatePalletCodes(CancelPalletBatchDTO dto, Integer operatorId) {
        if (dto == null || dto.getCodes() == null || dto.getCodes().isEmpty()) {
            throw new BusinessException("托盘码列表不能为空");
        }
        for (String rawCode : dto.getCodes()) {
            PalletCode palletCode = parseAndFind(rawCode);
            if (!"FREE".equalsIgnoreCase(palletCode.getStatus())) {
                throw new BusinessException("仅空闲托盘可作废");
            }
            palletCode.setStatus("INVALID");
            palletCode.setUpdatedBy(operatorId);
            palletCode.setUpdatedAt(LocalDateTime.now());
            this.updateById(palletCode);
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
            flow.setCycleNo(palletCode.getCurrentCycleNo());
            flow.setRemark(dto.getRemark());
            palletFlowRecordMapper.insert(flow);
        }
    }

    @Override
    @Transactional
    public void restoreInvalidPalletCodes(CancelPalletBatchDTO dto, Integer operatorId) {
        if (dto == null || dto.getCodes() == null || dto.getCodes().isEmpty()) {
            throw new BusinessException("二维码列表不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        for (String rawCode : dto.getCodes()) {
            PalletCode palletCode = parseAndFind(rawCode);
            if (!"INVALID".equalsIgnoreCase(palletCode.getStatus())) {
                throw new BusinessException("仅作废状态的二维码才可取消作废");
            }
            palletCode.setStatus("FREE");
            palletCode.setUpdatedBy(operatorId);
            palletCode.setUpdatedAt(now);
            this.updateById(palletCode);

            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCode.getId());
            flow.setOperationType("RESTORED");
            flow.setOperationName("取消作废二维码");
            flow.setOperationTime(now);
            flow.setOperatorId(operatorId);
            flow.setProductId(palletCode.getProductId());
            flow.setProductStatus(palletCode.getProductStatus());
            flow.setAssayId(palletCode.getAssayId());
            flow.setCycleNo(palletCode.getCurrentCycleNo());
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
        List<ResolvedPalletCode> codes = resolveAndSortTaskCodes(dto.getCodes());
        for (ResolvedPalletCode code : codes) {
            PalletCode palletCode = parseAndFindForUpdate(code.normalizedInput(), code.palletCodeId());
            cancelTasksForPallet(palletCode, operatorId, dto.getRemark());
        }
    }

    private void cancelTasksForPallet(PalletCode palletCode, Integer operatorId, String remark) {
        List<PalletTask> tasks = palletTaskMapper.selectList(
                new LambdaQueryWrapper<PalletTask>()
                        .eq(PalletTask::getPalletCodeId, palletCode.getId())
                        .eq(PalletTask::getStatus, "PENDING")
                        .eq(PalletTask::getCycleNo, getCurrentCycleNo(palletCode))
        );
        if (tasks.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PalletTask task : tasks) {
            task.setStatus("CANCELED");
            palletTaskMapper.updateById(task);
            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCode.getId());
            flow.setTaskId(task.getId());
            flow.setOperationType("CANCELED");
            flow.setOperationName("取消入库任务");
            flow.setOperationTime(now);
            flow.setOperatorId(operatorId);
            flow.setProductId(task.getProductId());
            flow.setProductStatus(task.getProductStatus());
            flow.setAssayId(task.getAssayId());
            flow.setCycleNo(task.getCycleNo());
            flow.setRemark(remark);
            palletFlowRecordMapper.insert(flow);
        }
        if ("PENDING".equalsIgnoreCase(palletCode.getStatus())) {
            releasePalletToFree(palletCode, operatorId);
        }
    }

    private void cancelPreviousCyclePendingTasks(PalletCode palletCode, Integer operatorId, String remark) {
        int currentCycleNo = getCurrentCycleNo(palletCode);
        if (currentCycleNo <= 0) {
            return;
        }
        List<PalletTask> tasks = palletTaskMapper.selectPreviousCyclePendingTasks(palletCode.getId(), currentCycleNo);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        palletTaskMapper.cancelPreviousCyclePendingTasks(palletCode.getId(), currentCycleNo, operatorId);
        LocalDateTime now = LocalDateTime.now();
        for (PalletTask task : tasks) {
            PalletFlowRecord flow = new PalletFlowRecord();
            flow.setPalletCodeId(palletCode.getId());
            flow.setTaskId(task.getId());
            flow.setOperationType("CANCELED");
            flow.setOperationName("自动取消旧轮次任务");
            flow.setOperationTime(now);
            flow.setOperatorId(operatorId);
            flow.setProductId(task.getProductId());
            flow.setProductStatus(task.getProductStatus());
            flow.setAssayId(task.getAssayId());
            flow.setCycleNo(task.getCycleNo());
            flow.setRemark(appendTraceRemark(remark, "任务ID=" + task.getId()));
            palletFlowRecordMapper.insert(flow);
        }
    }

    @Override
    // 通过托盘码关联化验；若缺失则按产品ID+生产日期补查最新版本，再回写托盘
    public PalletAssayVO getAssayByCode(String code) {
        PalletCode palletCode = parseAndFind(code);
        PalletTask latestWithAssay = palletTaskMapper.selectLatestWithAssay(
                palletCode.getId(),
                getCurrentCycleNo(palletCode)
        );
        Inventory inventory = lambdaQueryInventoryByPalletId(palletCode.getId());
        AssayResolveResult resolveResult = assayResolveService.previewForPallet(
                palletCode,
                latestWithAssay,
                inventory,
                null
        );
        Assay assay = resolveResult.getAssay();
        PalletAssayVO vo = new PalletAssayVO();
        fillAssayResolveMeta(vo, resolveResult);
        if (assay == null) {
            return vo;
        }
        vo.setId(assay.getId());
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

    private void fillAssayResolveMeta(PalletAssayVO vo, AssayResolveResult result) {
        vo.setResolveSource(result.getSource());
        vo.setResolveStatus(result.getStatus());
        vo.setResolveMessage(result.getMessage());
        vo.setAutoBound(result.isAutoBound());
        vo.setMultipleCandidates(result.isMultipleCandidates());
        vo.setCandidateCount(result.getCandidateCount());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int calculateRegisterTotalPieces(TaskSemiItemDTO itemDTO, Integer piecesPerPallet) {
        int boards = safeInt(itemDTO.getBoardCount());
        int pieces = safeInt(itemDTO.getPieceCount());
        int perPallet = safeInt(piecesPerPallet);
        if (boards < 0 || pieces < 0) {
            throw new BusinessException("登记半成品用量不能为负数");
        }
        if (perPallet <= 0) {
            throw new BusinessException("半成品未配置每板件数，无法登记用量");
        }
        if (pieces >= perPallet) {
            throw new BusinessException("件数必须少于每板件数，多出的部分请按板数登记");
        }
        int totalPieces = boards * perPallet + pieces;
        if (totalPieces <= 0) {
            throw new BusinessException("登记半成品用量必须大于0");
        }
        return totalPieces;
    }

    private BigDecimal resolveRemainingWeight(SemiPreparePoolBalance balance, int remainingPieces) {
        BigDecimal weightPerPiece = balance.getWeightPerPiece() == null ? BigDecimal.ZERO : balance.getWeightPerPiece();
        return weightPerPiece.multiply(BigDecimal.valueOf(remainingPieces));
    }

    @Override
    public String getTableName() {
        return "pallet_code";
    }
}
