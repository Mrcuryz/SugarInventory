package com.Laibin.SugarInventory.production.mapper;

import com.Laibin.SugarInventory.production.domain.po.ProductionDailyReportSection;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductionDailyReportSectionMapper extends BaseMapper<ProductionDailyReportSection> {
    @Select("SELECT * FROM production_daily_report_section WHERE report_id = #{reportId} ORDER BY id")
    List<ProductionDailyReportSection> selectByReportId(@Param("reportId") Long reportId);

    @Select("SELECT * FROM production_daily_report_section " +
            "WHERE report_id = #{reportId} AND department_code = #{departmentCode} LIMIT 1")
    ProductionDailyReportSection selectByReportAndDepartment(@Param("reportId") Long reportId,
                                                              @Param("departmentCode") String departmentCode);

    @Update("UPDATE production_daily_report_section SET status = 'DRAFT', version = version + 1, " +
            "updated_by = #{operatorId}, updated_by_name = #{operatorName}, updated_at = #{updatedAt} " +
            "WHERE id = #{id} AND version = #{version}")
    int updateForSave(@Param("id") Long id,
                      @Param("version") Integer version,
                      @Param("operatorId") Integer operatorId,
                      @Param("operatorName") String operatorName,
                      @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE production_daily_report_section SET status = 'SUBMITTED', version = version + 1, " +
            "updated_by = #{operatorId}, updated_by_name = #{operatorName}, updated_at = #{submittedAt}, " +
            "submitted_by = #{operatorId}, submitted_by_name = #{operatorName}, submitted_at = #{submittedAt} " +
            "WHERE id = #{id}")
    int submitSection(@Param("id") Long id,
                      @Param("operatorId") Integer operatorId,
                      @Param("operatorName") String operatorName,
                      @Param("submittedAt") LocalDateTime submittedAt);

    @Update("UPDATE production_daily_report_section SET status = 'SUBMITTED', version = version + 1, " +
            "updated_by = #{operatorId}, updated_by_name = #{operatorName}, updated_at = #{submittedAt}, " +
            "submitted_by = #{operatorId}, submitted_by_name = #{operatorName}, submitted_at = #{submittedAt} " +
            "WHERE report_id = #{reportId}")
    int submitByReportId(@Param("reportId") Long reportId,
                         @Param("operatorId") Integer operatorId,
                         @Param("operatorName") String operatorName,
                         @Param("submittedAt") LocalDateTime submittedAt);
}
