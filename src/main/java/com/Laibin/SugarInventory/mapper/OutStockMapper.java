package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.dto.OutRecordQueryDTO;
import com.Laibin.SugarInventory.domain.po.OutStock;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
import com.Laibin.SugarInventory.domain.vo.OutStockRecordVO;
import com.Laibin.SugarInventory.domain.vo.ProductVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutStockMapper extends BaseMapper<OutStock> {
    @Select("<script>" +
            "SELECT " +
            "   p.product_name, " +
            "   a.sample_date, " +
            "   a.color_value, " +
            "   a.reducing_sugar, " +
            "   a.ph_value, " +
            "   sm.mesh_name, " +
            "   iv.side, " +
            "   iv.`row_number`, " +
            "   w.warehouse_name, " +
            "   iv.layer, " +
            "   iv.quantity " +
            "FROM product p " +
            // 获取最新检测记录
            "INNER JOIN (" +
            "   SELECT product_id, MAX(sample_date) AS latest_date " +
            "   FROM assay " +
            "   GROUP BY product_id" +
            ") latest_st ON p.id = latest_st.product_id " +
            "LEFT JOIN assay a " +
            "   ON a.product_id = latest_st.product_id " +
            "   AND a.sample_date = latest_st.latest_date " +
            "LEFT JOIN inventory iv " +
            "   ON p.id = iv.product_id " +
            "LEFT JOIN screen_mesh sm " +
            "   ON iv.screen_mesh_id = sm.id " +
            "LEFT JOIN warehouse w " +
            "   ON iv.warehouse_id = w.id " +
            "WHERE p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            // 动态条件处理
            "<if test='query.startDate != null'>" +
            "   AND a.sample_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND a.sample_date &lt;= #{query.endDate} " +
            "</if> " +
            "<if test='query.colorValueMin != null'>" +
            "   AND a.color_value &gt;= #{query.colorValueMin} " +
            "</if> " +
            "<if test='query.colorValueMax != null'>" +
            "   AND a.color_value &lt;= #{query.colorValueMax} " +
            "</if> " +
            "<if test='query.reducingSugarMin != null'>" +
            "   AND a.reducing_sugar &gt;= #{query.reducingSugarMin} " +
            "</if> " +
            "<if test='query.reducingSugarMax != null'>" +
            "   AND a.reducing_sugar &lt;= #{query.reducingSugarMax} " +
            "</if> " +
            "<if test='query.phMin != null'>" +
            "   AND a.ph_value &gt;= #{query.phMin} " +
            "</if> " +
            "<if test='query.phMax != null'>" +
            "   AND a.ph_value &lt;= #{query.phMax} " +
            "</if> " +
            "<if test='query.screenMeshId != null'>" +
            "   AND sm.id = #{query.screenMeshId} " +
            "</if> " +
            // 按照先进后出的规则筛选
            "ORDER BY iv.layer DESC, iv.side ASC, iv.row_number DESC, a.sample_date DESC " +
            "</script>")
    List<OutProductVO> selectProductsByQuery(@Param("query") OutProductQueryDTO query);

    // 根据DTO批量查询出库记录
    @Select("<script>" +
            "SELECT " +
            "   w.warehouse_name, " +
            "   p.product_name, " +
            "   o.quantity, " +
            "   o.in_date, " +
            "   o.total_weight, " +
            "   o.out_date, " +
            "   u.name as operator_name, " +
            "   o.created_at " +
            "FROM out_stock o " +
            "INNER JOIN warehouse w ON o.warehouse_id = w.id " +
            "INNER JOIN product p ON o.product_id = p.id " +
            "INNER JOIN user u ON o.operator_id = u.id " +
            "WHERE 1 = 1 " +
            "<if test='query.warehouseName != null'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{query.warehouseName}, '%') " +
            "</if> " +
            "<if test='query.productName != null'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND o.in_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND o.in_date &lt;= #{query.endDate} " +
            "</if> " +
            "ORDER BY o.out_date DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<OutStockRecordVO> selectOutStockRecordsByQuery(@Param("query") OutRecordQueryDTO query,
                                                         @Param("offset") Integer offset,
                                                         @Param("size") Integer size
    );

    // 计算批量查询出库记录的总数
    @Select("<script>" +
            "SELECT COUNT(*) " +
            "FROM out_stock o " +
            "INNER JOIN warehouse w ON o.warehouse_id = w.id " +
            "INNER JOIN product p ON o.product_id = p.id " +
            "INNER JOIN user u ON o.operator_id = u.id " +
            "WHERE 1 = 1 " +
            "<if test='query.warehouseName != null'>" +
            "   AND w.warehouse_name LIKE CONCAT('%', #{query.warehouseName}, '%') " +
            "</if> " +
            "<if test='query.productName != null'>" +
            "   AND p.product_name LIKE CONCAT('%', #{query.productName}, '%') " +
            "</if> " +
            "<if test='query.startDate != null'>" +
            "   AND o.in_date &gt;= #{query.startDate} " +
            "</if> " +
            "<if test='query.endDate != null'>" +
            "   AND o.in_date &lt;= #{query.endDate} " +
            "</if> " +
            "</script>")
    Long countOutStockRecordsByQuery(@Param("query") OutRecordQueryDTO query);
}
