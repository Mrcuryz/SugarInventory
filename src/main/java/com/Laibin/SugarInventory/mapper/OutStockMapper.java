package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.OutProductQueryDTO;
import com.Laibin.SugarInventory.domain.po.OutStock;
import com.Laibin.SugarInventory.domain.vo.OutProductVO;
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
}
