package com.Laibin.SugarInventory.service.model;

public record ProductionPrepareLedgerSnapshot(
        int boardCountSnapshot,
        int pieceCountSnapshot,
        int totalPieces,
        Integer piecesPerPallet
) {
}
