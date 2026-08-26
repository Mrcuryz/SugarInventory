package com.Laibin.SugarInventory.production.service;

import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportHeaderSaveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportImportConfirmDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportSectionSaveDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductOptionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportListVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportImportVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

public interface ProductionDailyReportService {
    ProductionDailyReportVO getReport(LocalDate reportDate);

    PageResult<ProductionDailyReportListVO> pageReports(ProductionDailyReportQueryDTO query);

    ProductionDailyReportVO saveHeader(LocalDate reportDate, ProductionDailyReportHeaderSaveDTO dto,
                                       Integer operatorId, String operatorName);

    ProductionDailyReportVO saveSection(LocalDate reportDate, String departmentCode,
                                        ProductionDailyReportSectionSaveDTO dto,
                                        Integer operatorId, String operatorName);

    ProductionDailyReportVO submitSection(LocalDate reportDate, String departmentCode,
                                          Integer operatorId, String operatorName);

    ProductionDailyReportVO submitReport(LocalDate reportDate, Integer operatorId, String operatorName);

    List<ProductionDailyProductOptionVO> listProductOptions(String status, String type, String name);

    ExportedFile exportReport(LocalDate reportDate);

    ProductionDailyReportImportVO importReport(MultipartFile file, Integer operatorId, String operatorName);

    ProductionDailyReportVO confirmImport(ProductionDailyReportImportConfirmDTO dto,
                                          Integer operatorId, String operatorName);

    record ExportedFile(String filename, byte[] content) {
    }
}
