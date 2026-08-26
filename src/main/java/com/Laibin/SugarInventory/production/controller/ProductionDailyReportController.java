package com.Laibin.SugarInventory.production.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.annotation.LogOperation;
import com.Laibin.SugarInventory.common.PageResult;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.enumObject.OperationType;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportHeaderSaveDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportImportConfirmDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportQueryDTO;
import com.Laibin.SugarInventory.production.domain.dto.ProductionDailyReportSectionSaveDTO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyProductOptionVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportListVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportImportVO;
import com.Laibin.SugarInventory.production.domain.vo.ProductionDailyReportVO;
import com.Laibin.SugarInventory.production.service.ProductionDailyReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/production/daily-reports")
@RequiredArgsConstructor
@Tag(name = "生产日报")
public class ProductionDailyReportController {
    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProductionDailyReportService dailyReportService;

    @GetMapping
    @PreAuthorize("hasAuthority('production:daily-report:view')")
    public Result<PageResult<ProductionDailyReportListVO>> pageReports(ProductionDailyReportQueryDTO query) {
        return Result.success(dailyReportService.pageReports(query));
    }

    @GetMapping("/{reportDate}")
    @PreAuthorize("hasAuthority('production:daily-report:view')")
    public Result<ProductionDailyReportVO> getReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        return Result.success(dailyReportService.getReport(reportDate));
    }

    @GetMapping("/product-options")
    @PreAuthorize("hasAnyAuthority('production:daily-report:view', 'production:daily-report:edit')")
    public Result<List<ProductionDailyProductOptionVO>> listProductOptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name) {
        return Result.success(dailyReportService.listProductOptions(status, type, name));
    }

    @PutMapping("/{reportDate}/header")
    @PreAuthorize("hasAuthority('production:daily-report:edit')")
    @LogOperation(value = "production_daily_report", type = OperationType.UPDATE)
    public Result<ProductionDailyReportVO> saveHeader(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestBody @Valid ProductionDailyReportHeaderSaveDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(dailyReportService.saveHeader(reportDate, dto,
                loginUser.getUser().getId(), loginUser.getUser().getName()));
    }

    @PutMapping("/{reportDate}/sections/{departmentCode}")
    @PreAuthorize("hasAuthority('production:daily-report:edit')")
    @LogOperation(value = "production_daily_report_section", type = OperationType.UPDATE)
    public Result<ProductionDailyReportVO> saveSection(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @PathVariable String departmentCode,
            @RequestBody @Valid ProductionDailyReportSectionSaveDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(dailyReportService.saveSection(reportDate, departmentCode, dto,
                loginUser.getUser().getId(), loginUser.getUser().getName()));
    }

    @PostMapping("/{reportDate}/sections/{departmentCode}/submit")
    @PreAuthorize("hasAuthority('production:daily-report:edit')")
    @LogOperation(value = "production_daily_report_section", type = OperationType.UPDATE)
    public Result<ProductionDailyReportVO> submitSection(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @PathVariable String departmentCode,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(dailyReportService.submitSection(reportDate, departmentCode,
                loginUser.getUser().getId(), loginUser.getUser().getName()));
    }

    @PostMapping("/{reportDate}/submit")
    @PreAuthorize("hasAuthority('production:daily-report:edit')")
    @LogOperation(value = "production_daily_report", type = OperationType.UPDATE)
    public Result<ProductionDailyReportVO> submitReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(dailyReportService.submitReport(reportDate,
                loginUser.getUser().getId(), loginUser.getUser().getName()));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('production:daily-report:edit')")
    @LogOperation(value = "production_daily_report_excel", type = OperationType.UPDATE)
    public Result<ProductionDailyReportImportVO> importReport(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(dailyReportService.importReport(file,
                loginUser.getUser().getId(), loginUser.getUser().getName()));
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasAuthority('production:daily-report:edit')")
    @LogOperation(value = "production_daily_report_excel", type = OperationType.UPDATE)
    public Result<ProductionDailyReportVO> confirmImport(
            @RequestBody @Valid ProductionDailyReportImportConfirmDTO dto,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(dailyReportService.confirmImport(dto,
                loginUser.getUser().getId(), loginUser.getUser().getName()));
    }

    @GetMapping("/{reportDate}/export")
    @PreAuthorize("hasAuthority('production:daily-report:export')")
    public ResponseEntity<byte[]> exportReport(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate) {
        ProductionDailyReportService.ExportedFile exported = dailyReportService.exportReport(reportDate);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exported.content());
    }
}
