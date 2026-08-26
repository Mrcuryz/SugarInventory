package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportQueryDTO;
import com.Laibin.SugarInventory.production.domain.po.ProductionDailyReport;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportListVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductionDailyReportMapper extends BaseMapper<ProductionDailyReport> {
    @Select("SELECT * FROM production_daily_report WHERE report_date = #{reportDate} LIMIT 1")
    ProductionDailyReport selectByReportDate(@Param("reportDate") LocalDate reportDate);

    @Update("UPDATE production_daily_report SET prepared_date = #{preparedDate}, " +
            "prepared_by_name = #{preparedByName}, status = 'DRAFT', version = version + 1, " +
            "updated_by = #{operatorId}, updated_by_name = #{operatorName}, updated_at = #{updatedAt} " +
            "WHERE id = #{id} AND version = #{version}")
    int updateHeaderWithVersion(@Param("id") Long id,
                                @Param("version") Integer version,
                                @Param("preparedDate") LocalDate preparedDate,
                                @Param("preparedByName") String preparedByName,
                                @Param("operatorId") Integer operatorId,
                                @Param("operatorName") String operatorName,
                                @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE production_daily_report SET status = 'DRAFT', updated_by = #{operatorId}, " +
            "updated_by_name = #{operatorName}, updated_at = #{updatedAt} WHERE id = #{id}")
    int touchDraft(@Param("id") Long id,
                   @Param("operatorId") Integer operatorId,
                   @Param("operatorName") String operatorName,
                   @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE production_daily_report SET status = 'SUBMITTED', version = version + 1, " +
            "updated_by = #{operatorId}, updated_by_name = #{operatorName}, updated_at = #{submittedAt}, " +
            "submitted_by = #{operatorId}, submitted_by_name = #{operatorName}, submitted_at = #{submittedAt} " +
            "WHERE id = #{id}")
    int submitReport(@Param("id") Long id,
                     @Param("operatorId") Integer operatorId,
                     @Param("operatorName") String operatorName,
                     @Param("submittedAt") LocalDateTime submittedAt);

    @Select("<script>" +
            "SELECT id, report_date AS reportDate, prepared_date AS preparedDate, " +
            "prepared_by_name AS preparedByName, status, updated_by_name AS updatedByName, updated_at AS updatedAt " +
            "FROM production_daily_report WHERE 1=1 " +
            "<if test='query.startDate != null'>AND report_date &gt;= #{query.startDate} </if>" +
            "<if test='query.endDate != null'>AND report_date &lt;= #{query.endDate} </if>" +
            "<if test='query.status != null and query.status != \"\"'>AND status = #{query.status} </if>" +
            "ORDER BY report_date DESC LIMIT #{offset}, #{size}" +
            "</script>")
    List<ProductionDailyReportListVO> pageReports(@Param("query") ProductionDailyReportQueryDTO query,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM production_daily_report WHERE 1=1 " +
            "<if test='query.startDate != null'>AND report_date &gt;= #{query.startDate} </if>" +
            "<if test='query.endDate != null'>AND report_date &lt;= #{query.endDate} </if>" +
            "<if test='query.status != null and query.status != \"\"'>AND status = #{query.status} </if>" +
            "</script>")
    long countReports(@Param("query") ProductionDailyReportQueryDTO query);
}
