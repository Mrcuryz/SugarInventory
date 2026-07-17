package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.po.Inventory;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutWarehouseVO;
import com.Laibin.SugarInventory.domain.vo.VInventorySummary;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Select("SELECT * FROM inventory WHERE in_stock_id = #{inStockId}")
    Inventory selectByInStockId(@Param("inStockId") Integer inStockId);

    @Select("SELECT side, `row_number` FROM inventory WHERE warehouse_id = #{warehouseId}")
    List<Inventory> selectByWarehouseOrdered(@Param("warehouseId") Integer warehouseId);

    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "ORDER BY CASE side WHEN '右' THEN 1 ELSE 2 END, `row_number` DESC")
    List<Inventory> getInventoryStackOrder(@Param("warehouseId") Integer warehouseId);

    @Select("SELECT COUNT(DISTINCT `row_number`) " +
            "FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} " +
            "AND layer = #{layer}")
    int getUsedRows(@Param("warehouseId") int warehouseId,
                    @Param("side") String side,
                    @Param("layer") int layer);

    @Select("SELECT `row_number` " +
            "FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} " +
            "AND layer = #{layer} " +
            "ORDER BY `row_number` ASC")
    List<Integer> getUsedRowList(@Param("warehouseId") int warehouseId,
                                 @Param("side") String side,
                                 @Param("layer") int layer);

    @Select("SELECT `row_number` " +
            "FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} " +
            "AND layer = #{layer} " +
            "ORDER BY `row_number` ASC FOR UPDATE")
    List<Integer> getUsedRowListForUpdate(@Param("warehouseId") int warehouseId,
                                          @Param("side") String side,
                                          @Param("layer") int layer);


    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND product_id = #{productId} " +
            "AND entry_date = #{entryDate} " +
            "AND screen_mesh_id = #{screenMeshId}")
    Inventory existSameInventory(@Param("warehouseId") Integer warehouseId,
                                 @Param("productId") Integer productId,
                                 @Param("entryDate") LocalDate entryDate,
                                 @Param("screenMeshId") Integer screenMeshId);

    @Insert("INSERT INTO inventory (warehouse_id, product_id, entry_date, side, `row_number`, layer, quantity, " +
            "screen_mesh_id, assay_id, created_at, in_stock_id, semi_record_id, product_status, pieces, pallet_code_id) " +
            "VALUES (#{warehouseId}, #{productId}, #{entryDate}, #{side}, #{rowNumber}, #{layer}, #{quantity}, " +
            "#{screenMeshId}, #{assayId}, #{createdAt}, #{inStockId}, #{semiRecordId}, #{productStatus}, #{pieces}, #{palletCodeId} )")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Inventory inventory);

    @Update("UPDATE inventory SET warehouse_id = #{warehouseId}, side = #{side}, `row_number` = #{rowNumber}, layer = #{layer} WHERE id = #{id}")
    int updateLocation(@Param("id") Integer id,
                       @Param("warehouseId") Integer warehouseId,
                       @Param("side") String side,
                       @Param("rowNumber") Integer rowNumber,
                       @Param("layer") Integer layer);

    @Select("SELECT * " +
            "FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "ORDER BY layer DESC LIMIT 1")
    Inventory getLast(Integer warehouseId);

    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} " +
            "AND pallet_code_id IS NOT NULL " +
            "ORDER BY layer DESC, `row_number` DESC " +
            "LIMIT #{limit}")
    List<Inventory> selectFrontPalletsForOperation(@Param("warehouseId") Integer warehouseId,
                                                   @Param("side") String side,
                                                   @Param("limit") Integer limit);

    // **查询库存（按先进后出）**
    @Select("SELECT * FROM inventory " +
            "WHERE warehouse_id = #{warehouseId} " +
            "AND side = #{side} AND layer = #{layer} " +
            "ORDER BY `row_number` DESC")
    List<Inventory> getInventoryForOutStock(@Param("warehouseId") int warehouseId,
                                            @Param("side") String side,
                                            @Param("layer") int layer);

    // **删除某个格子的库存**
    @Delete("DELETE FROM inventory WHERE id = #{id}")
    void deleteInventoryById(@Param("id") int id);

    @Select("<script>" +
            "SELECT DISTINCT w.id AS warehouse_id, w.warehouse_name " +
            "FROM inventory i " +
            "INNER JOIN warehouse w ON i.warehouse_id = w.id " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "LEFT JOIN assay a ON i.assay_id = a.id " +
            "LEFT JOIN screen_mesh sm ON i.screen_mesh_id = sm.id " +
            "LEFT JOIN pallet_code pc ON i.pallet_code_id = pc.id " +
            "WHERE 1=1 " +
            "<if test='query.palletCodeList != null and query.palletCodeList.size() > 0'>" +
            "   AND pc.code IN " +
            "   <foreach item='palletCode' collection='query.palletCodeList' open='(' separator=',' close=')'>" +
            "       #{palletCode}" +
            "   </foreach>" +
            "</if> " +
            "<if test='query.productName != null and query.productName != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.standardNames != null and query.standardNames != \"\"'> " +
            "   AND JSON_CONTAINS(a.qualified_standards, JSON_QUOTE(#{query.standardNames})) " +
            "</if> " +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND i.entry_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND i.entry_date &lt;= #{query.endDate} " +
            "</if> " +
            "ORDER BY w.warehouse_name" +
            "</script>")
    List<OutWarehouseVO> findWarehousesByCondition(@Param("query") OutProductQueryDTO query);

    @Select("<script>" +
            "SELECT i.id AS inventoryId, i.product_id AS productId, p.product_name AS productName, " +
            "       w.warehouse_name AS warehouseName, i.entry_date AS sampleDate, p.product_type AS productType, " +
            "       i.product_status AS productStatus, JSON_UNQUOTE(JSON_EXTRACT(a.qualified_standards, '$')) AS standardNames, " +
            "       sm.mesh_name AS meshName, i.side AS side, i.row_number AS rowNumber, i.layer AS layer, " +
            "       i.quantity AS quantity, i.pieces AS pieces, pc.code AS palletCode, i.created_at AS createdAt " +
            "FROM inventory i " +
            "INNER JOIN warehouse w ON i.warehouse_id = w.id " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "LEFT JOIN assay a ON i.assay_id = a.id " +
            "LEFT JOIN screen_mesh sm ON i.screen_mesh_id = sm.id " +
            "LEFT JOIN pallet_code pc ON i.pallet_code_id = pc.id " +
            "WHERE i.warehouse_id = #{warehouseId} " +
            "<if test='query.palletCodeList != null and query.palletCodeList.size() > 0'>" +
            "   AND pc.code IN " +
            "   <foreach item='palletCode' collection='query.palletCodeList' open='(' separator=',' close=')'>" +
            "       #{palletCode}" +
            "   </foreach>" +
            "</if> " +
            "<if test='query.productName != null and query.productName != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.standardNames != null and query.standardNames != \"\"'> " +
            "   AND JSON_CONTAINS(a.qualified_standards, JSON_QUOTE(#{query.standardNames})) " +
            "</if>" +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND i.entry_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND i.entry_date &lt;= #{query.endDate} " +
            "</if> " +
            "ORDER BY i.entry_date DESC, w.warehouse_name, i.side, i.`row_number` ASC " +
            "</script>")
    List<OutProductVO> findInventoryByWarehouse(@Param("warehouseId") Integer warehouseId,
                                                @Param("query") OutProductQueryDTO query);

    @Select("<script>" +
            "SELECT MIN(i.id) AS inventoryId, i.product_id AS productId, p.product_name AS productName, " +
            "       w.warehouse_name AS warehouseName, MIN(i.entry_date) AS sampleDate, p.product_type AS productType, " +
            "       i.product_status AS productStatus, " +
            "       GROUP_CONCAT(DISTINCT JSON_UNQUOTE(JSON_EXTRACT(a.qualified_standards, '$')) ORDER BY a.id SEPARATOR '、') AS standardNames, " +
            "       GROUP_CONCAT(DISTINCT sm.mesh_name ORDER BY sm.mesh_name SEPARATOR '、') AS meshName, " +
            "       SUM(CASE WHEN COALESCE(i.pieces, 0) > 0 THEN 0 ELSE COALESCE(i.quantity, 0) END) AS quantity, " +
            "       SUM(COALESCE(i.pieces, 0)) AS pieces, MAX(i.created_at) AS createdAt " +
            "FROM inventory i " +
            "INNER JOIN warehouse w ON i.warehouse_id = w.id " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "LEFT JOIN assay a ON i.assay_id = a.id " +
            "LEFT JOIN screen_mesh sm ON i.screen_mesh_id = sm.id " +
            "LEFT JOIN pallet_code pc ON i.pallet_code_id = pc.id " +
            "WHERE i.warehouse_id = #{warehouseId} " +
            "<if test='query.palletCodeList != null and query.palletCodeList.size() > 0'>" +
            "   AND pc.code IN " +
            "   <foreach item='palletCode' collection='query.palletCodeList' open='(' separator=',' close=')'>" +
            "       #{palletCode}" +
            "   </foreach>" +
            "</if> " +
            "<if test='query.productName != null and query.productName != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.standardNames != null and query.standardNames != \"\"'> " +
            "   AND JSON_CONTAINS(a.qualified_standards, JSON_QUOTE(#{query.standardNames})) " +
            "</if>" +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND i.entry_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND i.entry_date &lt;= #{query.endDate} " +
            "</if> " +
            "GROUP BY i.product_id, p.product_name, w.warehouse_name, p.product_type, i.product_status " +
            "ORDER BY MIN(i.entry_date) ASC, p.product_name ASC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<OutProductVO> pageInventoryByWarehouse(@Param("warehouseId") Integer warehouseId,
                                                @Param("query") OutProductQueryDTO query,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM (" +
            "SELECT i.product_id, i.product_status " +
            "FROM inventory i " +
            "INNER JOIN product p ON i.product_id = p.id " +
            "LEFT JOIN assay a ON i.assay_id = a.id " +
            "LEFT JOIN screen_mesh sm ON i.screen_mesh_id = sm.id " +
            "LEFT JOIN pallet_code pc ON i.pallet_code_id = pc.id " +
            "WHERE i.warehouse_id = #{warehouseId} " +
            "<if test='query.palletCodeList != null and query.palletCodeList.size() > 0'>" +
            "   AND pc.code IN " +
            "   <foreach item='palletCode' collection='query.palletCodeList' open='(' separator=',' close=')'>" +
            "       #{palletCode}" +
            "   </foreach>" +
            "</if> " +
            "<if test='query.productName != null and query.productName != \"\"'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.standardNames != null and query.standardNames != \"\"'> " +
            "   AND JSON_CONTAINS(a.qualified_standards, JSON_QUOTE(#{query.standardNames})) " +
            "</if>" +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND i.entry_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND i.entry_date &lt;= #{query.endDate} " +
            "</if> " +
            "GROUP BY i.product_id, i.product_status" +
            ") grouped_inventory" +
            "</script>")
    Long countInventoryByWarehouse(@Param("warehouseId") Integer warehouseId,
                                   @Param("query") OutProductQueryDTO query);


    @Select("select * from inventory where warehouse_id = #{warehouseId} AND entry_date = #{entryDate}  AND product_id = #{productId}  AND pieces > 0 AND pieces < #{piecesPerPallet}")
    List<Inventory> getHasPiecesRows(@Param("warehouseId") Integer warehouseId,
                                     @Param("entryDate") LocalDate entryDate,
                                     @Param("productId") Integer productId,
                                     @Param("piecesPerPallet") Integer piecesPerPallet);

    @Update("update inventory set pieces = #{pieces} where id = #{id}")
    void updatePieces(@Param("id") Integer id, @Param("pieces") Integer pieces);

    @Update("UPDATE inventory SET assay_id = #{assayId} WHERE id = #{id}")
    int updateAssayById(@Param("id") Integer id, @Param("assayId") Integer assayId);

    @Update("UPDATE inventory SET assay_id = #{assayId} WHERE pallet_code_id = #{palletCodeId}")
    int updateAssayByPalletCodeId(@Param("palletCodeId") Integer palletCodeId,
                                  @Param("assayId") Integer assayId);

    @Select("SELECT EXISTS(SELECT 1 FROM inventory WHERE product_id = #{productId} LIMIT 1)")
    boolean existsByProductId(@Param("productId") Integer productId);

}
