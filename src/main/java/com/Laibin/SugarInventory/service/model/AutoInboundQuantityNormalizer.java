package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.redis.AutoInboundTaskItem;

import java.util.ArrayList;
import java.util.List;

public final class AutoInboundQuantityNormalizer {
    private AutoInboundQuantityNormalizer() {
    }

    public static List<AutoInboundTaskItem> normalize(Integer boardCount, Integer pieceCount,
                                                       Integer piecesPerPallet, String scene) {
        PalletInventoryOccupancyRule.validatePiecesPerPallet(piecesPerPallet, scene);
        int boards = boardCount == null ? 0 : boardCount;
        int pieces = pieceCount == null ? 0 : pieceCount;
        if (boards < 0 || pieces < 0) {
            throw new IllegalArgumentException(scene + "的板数和件数不能为负数");
        }
        if (boards == 0 && pieces == 0) {
            throw new IllegalArgumentException(scene + "的板数和件数不能同时为0");
        }

        PalletInventoryOccupancyRule.PieceSplit pieceSplit =
                pieces > 0 ? PalletInventoryOccupancyRule.splitPieces(pieces, piecesPerPallet, scene)
                        : new PalletInventoryOccupancyRule.PieceSplit(0, 0);

        int finalBoards = boards + pieceSplit.fullPalletCount();
        int loosePieces = pieceSplit.loosePieces();
        List<AutoInboundTaskItem> items = new ArrayList<>(finalBoards + (loosePieces > 0 ? 1 : 0));
        int seq = 1;
        for (int i = 0; i < finalBoards; i++) {
            items.add(createItem(seq++, 1, "0", "1板"));
        }
        if (loosePieces > 0) {
            items.add(createItem(seq, loosePieces, "1", loosePieces + "件，占1板位"));
        }
        return items;
    }

    private static AutoInboundTaskItem createItem(Integer seq, Integer quantity, String unit, String displayQuantity) {
        AutoInboundTaskItem item = new AutoInboundTaskItem();
        item.setSeq(seq);
        item.setQuantity(quantity);
        item.setUnit(unit);
        item.setDisplayQuantity(displayQuantity);
        item.setStatus("PENDING");
        return item;
    }
}
