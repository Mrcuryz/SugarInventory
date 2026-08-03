package com.Laibin.SugarInventory.analytics.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.analytics.domain.dto.RegisteredReportRunQueryDTO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportHistoryItemVO;
import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportRunVO;
import com.Laibin.SugarInventory.analytics.service.RegisteredReportService;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportArchiveService;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics/agent-read")
@RequiredArgsConstructor
public class AnalyticsAgentReadController {
    private final RegisteredReportService registeredReportService;
    private final RegisteredReportArchiveService reportArchiveService;

    @PostMapping("/reports/run")
    @PreAuthorize("hasAnyAuthority('production:order:view', 'assay:view', 'task:view', 'inventory:view')")
    public Result<RegisteredReportRunVO> runRegisteredReport(
            @RequestBody RegisteredReportRunQueryDTO query,
            @AuthenticationPrincipal LoginUser loginUser) {
        RegisteredReportRunVO report = registeredReportService.run(query);
        return Result.success(reportArchiveService.persist(
                report,
                loginUser.getUser().getId(),
                loginUser.getUser().getName()));
    }

    @GetMapping("/reports/{reportRunId}")
    @PreAuthorize("hasAnyAuthority('production:order:view', 'assay:view', 'task:view', 'inventory:view')")
    public Result<RegisteredReportRunVO> getRegisteredReport(
            @PathVariable String reportRunId,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(reportArchiveService.load(
                reportRunId,
                loginUser.getUser().getId(),
                authorityCodes(loginUser)));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('production:order:view', 'assay:view', 'task:view', 'inventory:view')")
    public Result<PageResult<RegisteredReportHistoryItemVO>> listRegisteredReports(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String reportDefinitionId,
            @AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(reportArchiveService.list(
                loginUser.getUser().getId(),
                authorityCodes(loginUser),
                page,
                size,
                reportDefinitionId));
    }

    @GetMapping("/reports/{reportRunId}/export.xlsx")
    @PreAuthorize("hasAnyAuthority('production:order:view', 'assay:view', 'task:view', 'inventory:view')")
    public ResponseEntity<byte[]> exportRegisteredReport(
            @PathVariable String reportRunId,
            @AuthenticationPrincipal LoginUser loginUser) {
        RegisteredReportArchiveService.ExportedReportFile exported =
                reportArchiveService.exportXlsx(
                        reportRunId,
                        loginUser.getUser().getId(),
                        loginUser.getUser().getName(),
                        authorityCodes(loginUser));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(exported.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Report-Export-Audit-Ref", exported.auditRef())
                .body(exported.content());
    }

    private static Set<String> authorityCodes(LoginUser loginUser) {
        return loginUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
