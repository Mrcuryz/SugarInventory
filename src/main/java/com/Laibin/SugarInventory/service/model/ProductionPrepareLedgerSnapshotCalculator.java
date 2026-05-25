package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.Product;

public final class ProductionPrepareLedgerSnapshotCalculator {

    private ProductionPrepareLedgerSnapshotCalculator() {
    }

    public static ProductionPrepareLedgerSnapshot calculate(Inventory inventory, Product product) {
        if (inventory == null) {
            throw new IllegalArgumentException("库存记录不能为空");
        }
        int pieces = inventory.getPieces() == null ? 0 : inventory.getPieces();
        Integer piecesPerPallet = product == null ? null : product.getPiecesPerPallet();
        if (pieces > 0) {
            return new ProductionPrepareLedgerSnapshot(0, pieces, pieces, piecesPerPallet);
        }

        int boards = inventory.getQuantity() == null || inventory.getQuantity() <= 0 ? 1 : inventory.getQuantity();
        if (piecesPerPallet == null || piecesPerPallet <= 0) {
            throw new IllegalArgumentException("产品每板件数未配置，无法折算生产占用件数");
        }
        return new ProductionPrepareLedgerSnapshot(boards, 0, boards * piecesPerPallet, piecesPerPallet);
    }
}
