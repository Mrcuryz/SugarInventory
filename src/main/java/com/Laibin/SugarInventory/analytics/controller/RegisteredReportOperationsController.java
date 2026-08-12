package com.Laibin.SugarInventory.analytics.controller;

import com.Laibin.SugarInventory.analytics.domain.vo.RegisteredReportOperationsStatusVO;
import com.Laibin.SugarInventory.analytics.service.impl.RegisteredReportOperationsReadService;
import com.Laibin.SugarInventory.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics/operations")
@RequiredArgsConstructor
public class RegisteredReportOperationsController {
    private final RegisteredReportOperationsReadService readService;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('log:view')")
    public Result<RegisteredReportOperationsStatusVO> latestStatus() {
        return Result.success(readService.latestStatus());
    }
}
