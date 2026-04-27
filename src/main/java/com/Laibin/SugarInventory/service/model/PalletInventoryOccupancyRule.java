package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.common.BusinessException;

public final class PalletInventoryOccupancyRule {
    private PalletInventoryOccupancyRule() {
    }

    public static void validatePiecesPerPallet(Integer piecesPerPallet, String scene) {
        if (piecesPerPallet == null || piecesPerPallet <= 0) {
            throw new BusinessException(scene + "缺少每板件数配置");
        }
    }

    public static void validateSingleQrInventory(String unit, Integer quantity, Integer piecesPerPallet, String scene) {
        validatePiecesPerPallet(piecesPerPallet, scene);
        int safeQuantity = quantity == null ? 0 : quantity;
        if ("0".equals(unit)) {
            if (safeQuantity != 1) {
                throw new BusinessException(scene + "仅允许单个二维码承载 1 板；多板请拆成多个二维码");
            }
            return;
        }
        if (!"1".equals(unit)) {
            throw new BusinessException(scene + "的单位仅支持板或件");
        }
        if (safeQuantity <= 0) {
            throw new BusinessException(scene + "的散件数量必须大于 0");
        }
        if (safeQuantity >= piecesPerPallet) {
            throw new BusinessException(scene + "的散件数量必须少于每板件数；整板请使用 1 板");
        }
    }

    public static PieceSplit splitPieces(Integer quantity, Integer piecesPerPallet, String scene) {
        validatePiecesPerPallet(piecesPerPallet, scene);
        int safeQuantity = quantity == null ? 0 : quantity;
        if (safeQuantity <= 0) {
            throw new BusinessException(scene + "的件数必须大于 0");
        }
        return new PieceSplit(safeQuantity / piecesPerPallet, safeQuantity % piecesPerPallet);
    }

    public record PieceSplit(int fullPalletCount, int loosePieces) {
    }
}
