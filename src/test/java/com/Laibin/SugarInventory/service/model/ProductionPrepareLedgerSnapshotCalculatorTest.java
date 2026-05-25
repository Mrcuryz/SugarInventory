package com.Laibin.SugarInventory.service.model;

import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.po.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionPrepareLedgerSnapshotCalculatorTest {

    @Test
    void calculateFullBoardAsPieces() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(2);
        inventory.setPieces(0);
        Product product = new Product();
        product.setPiecesPerPallet(25);

        ProductionPrepareLedgerSnapshot snapshot =
                ProductionPrepareLedgerSnapshotCalculator.calculate(inventory, product);

        assertEquals(2, snapshot.boardCountSnapshot());
        assertEquals(0, snapshot.pieceCountSnapshot());
        assertEquals(50, snapshot.totalPieces());
        assertEquals(25, snapshot.piecesPerPallet());
    }

    @Test
    void calculateLoosePiecesDirectly() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(1);
        inventory.setPieces(10);
        Product product = new Product();
        product.setPiecesPerPallet(25);

        ProductionPrepareLedgerSnapshot snapshot =
                ProductionPrepareLedgerSnapshotCalculator.calculate(inventory, product);

        assertEquals(0, snapshot.boardCountSnapshot());
        assertEquals(10, snapshot.pieceCountSnapshot());
        assertEquals(10, snapshot.totalPieces());
    }

    @Test
    void rejectFullBoardWithoutPiecesPerPallet() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(1);
        inventory.setPieces(0);
        Product product = new Product();

        assertThrows(IllegalArgumentException.class,
                () -> ProductionPrepareLedgerSnapshotCalculator.calculate(inventory, product));
    }
}
