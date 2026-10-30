package com.Laibin.SugarInventory.service;

import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.domain.po.Warehouse;
import com.Laibin.SugarInventory.domain.vo.WarehouseLocationCodeVO;
import com.Laibin.SugarInventory.domain.enumObject.ErrorCode;
import com.Laibin.SugarInventory.mapper.WarehouseMapper;
import com.Laibin.SugarInventory.util.PalletQrLabelPdfRenderer;
import com.Laibin.SugarInventory.util.QrCodeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WarehouseLocationCodeService {
    static final String CODE_PREFIX = "LSI|WAREHOUSE|1|";
    private static final Pattern CODE_PATTERN = Pattern.compile("^LSI\\|WAREHOUSE\\|1\\|([1-9]\\d{0,9})$");

    private final WarehouseMapper warehouseMapper;

    public WarehouseLocationCodeVO getLocationCode(Integer warehouseId) {
        Warehouse warehouse = requireWarehouse(warehouseId);
        return toVO(warehouse);
    }

    public WarehouseLocationCodeVO resolve(String rawCode) {
        Integer warehouseId = parseWarehouseId(rawCode);
        return toVO(requireWarehouse(warehouseId));
    }

    public List<WarehouseLocationCodeVO> getLocationCodes(List<Integer> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            throw new BusinessException("请选择至少一个库位");
        }
        return new LinkedHashSet<>(warehouseIds).stream()
                .map(this::getLocationCode)
                .toList();
    }

    public byte[] generateQrCodePng(Integer warehouseId) {
        WarehouseLocationCodeVO location = getLocationCode(warehouseId);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(QrCodeUtils.generateQrCode(location.getCode(), 512, 512), "PNG", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("生成库位二维码失败");
        }
    }

    public byte[] generateQrLabelPdf(Integer warehouseId) {
        return generateQrLabelPdf(Collections.singletonList(warehouseId));
    }

    public byte[] generateQrLabelPdf(List<Integer> warehouseIds) {
        List<PalletQrLabelPdfRenderer.LabelPayload> labels = getLocationCodes(warehouseIds).stream()
                .map(location -> new PalletQrLabelPdfRenderer.LabelPayload(
                        location.getCode(),
                        "库位 " + location.getWarehouseName(),
                        location.getWarehouseName()))
                .toList();
        try {
            return PalletQrLabelPdfRenderer.renderA4LabelsWithTitle(labels);
        } catch (IOException exception) {
            throw new BusinessException("生成库位二维码标签失败");
        }
    }

    static Integer parseWarehouseId(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException("库位码不能为空");
        }
        String normalized = rawCode.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = CODE_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            throw new BusinessException("无法识别库位码，请扫描系统生成的库位标签");
        }
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new BusinessException("库位码中的库位ID无效");
        }
    }

    private Warehouse requireWarehouse(Integer warehouseId) {
        if (warehouseId == null || warehouseId <= 0) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        return warehouse;
    }

    private WarehouseLocationCodeVO toVO(Warehouse warehouse) {
        return new WarehouseLocationCodeVO(
                CODE_PREFIX + warehouse.getId(),
                warehouse.getId(),
                warehouse.getWarehouseName(),
                warehouse.getStatus(),
                warehouse.getCurCapacity(),
                warehouse.getMaxCapacity());
    }
}
