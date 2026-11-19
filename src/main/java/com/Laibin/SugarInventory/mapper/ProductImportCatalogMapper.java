package com.Laibin.SugarInventory.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ProductImportCatalogMapper {
    @Data
    class Entry {
        private Integer id, piecesPerPallet;
        private String productName, productType, status, packagingMethod, screenMeshName;
        private Boolean canStack, hasInventory;
        private BigDecimal weightPerPiece;
    }
    // One snapshot for name, specification and inventory lock; the extra row detects truncation.
    @Select("""
        SELECT p.id, p.product_name AS productName, p.product_type AS productType, p.status,
               p.packaging_method AS packagingMethod, p.weight_per_piece AS weightPerPiece,
               p.pieces_per_pallet AS piecesPerPallet, p.can_stack AS canStack,
               s.mesh_name AS screenMeshName, EXISTS(SELECT 1 FROM inventory i WHERE i.product_id=p.id) AS hasInventory
        FROM product p LEFT JOIN screen_mesh s ON s.id=p.screen_mesh_id ORDER BY p.id LIMIT 501
        """)
    List<Entry> readForMatching();
}
