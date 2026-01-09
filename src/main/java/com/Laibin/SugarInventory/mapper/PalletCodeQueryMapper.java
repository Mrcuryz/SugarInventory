package com.Laibin.SugarInventory.mapper;

import com.Laibin.SugarInventory.domain.dto.PalletCodeQueryDTO;
import com.Laibin.SugarInventory.domain.vo.PalletCodePageVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PalletCodeQueryMapper {

    @Select({
            "<script>",
            "SELECT ",
            " pc.id, pc.code, pc.status, pc.product_status AS productStatus, pc.production_date AS productionDate,",
            " pc.assay_id AS assayId, pc.created_at AS createdAt, pc.updated_at AS updatedAt,",
            " p.product_name AS productName, p.product_type AS productType,",
            " sm.mesh_name AS screenMeshName,",
            " cu.name AS createdByName, uu.name AS updatedByName",
            " FROM pallet_code pc",
            " LEFT JOIN product p ON pc.product_id = p.id",
            " LEFT JOIN screen_mesh sm ON pc.screen_mesh_id = sm.id",
            " LEFT JOIN user cu ON pc.created_by = cu.id",
            " LEFT JOIN user uu ON pc.updated_by = uu.id",
            " WHERE 1=1",
            " <if test='q.code != null and q.code != \"\"'>",
            "   AND pc.code = #{q.code}",
            " </if>",
            " <if test='q.productName != null and q.productName != \"\"'>",
            "   AND p.product_name LIKE CONCAT('%', #{q.productName}, '%')",
            " </if>",
            " <if test='q.productType != null and q.productType != \"\"'>",
            "   AND p.product_type = #{q.productType}",
            " </if>",
            " <if test='q.productStatus != null and q.productStatus != \"\"'>",
            "   AND pc.product_status = #{q.productStatus}",
            " </if>",
            " <if test='q.productionDateStart != null'>",
            "   AND pc.production_date &gt;= #{q.productionDateStart}",
            " </if>",
            " <if test='q.productionDateEnd != null'>",
            "   AND pc.production_date &lt;= #{q.productionDateEnd}",
            " </if>",
            " ORDER BY pc.id ASC",
            " LIMIT #{offset}, #{size}",
            "</script>"
    })
    java.util.List<PalletCodePageVO> pagePalletCodes(@Param("q") PalletCodeQueryDTO q,
                                                     @Param("offset") long offset,
                                                     @Param("size") long size);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            " FROM pallet_code pc",
            " LEFT JOIN product p ON pc.product_id = p.id",
            " WHERE 1=1",
            " <if test='q.code != null and q.code != \"\"'>",
            "   AND pc.code = #{q.code}",
            " </if>",
            " <if test='q.productName != null and q.productName != \"\"'>",
            "   AND p.product_name LIKE CONCAT('%', #{q.productName}, '%')",
            " </if>",
            " <if test='q.productType != null and q.productType != \"\"'>",
            "   AND p.product_type = #{q.productType}",
            " </if>",
            " <if test='q.productStatus != null and q.productStatus != \"\"'>",
            "   AND pc.product_status = #{q.productStatus}",
            " </if>",
            " <if test='q.productionDateStart != null'>",
            "   AND pc.production_date &gt;= #{q.productionDateStart}",
            " </if>",
            " <if test='q.productionDateEnd != null'>",
            "   AND pc.production_date &lt;= #{q.productionDateEnd}",
            " </if>",
            "</script>"
    })
    Long countPalletCodes(@Param("q") PalletCodeQueryDTO q);
}
