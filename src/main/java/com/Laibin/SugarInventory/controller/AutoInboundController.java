package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.service.AutoInboundConfirmService;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auto-inbound")
@RequiredArgsConstructor
public class AutoInboundController {

    private final AutoInboundParseService autoInboundParseService;
    private final AutoInboundConfirmService autoInboundConfirmService;

    @PostMapping("/parse")
    public Result<AutoInboundParseResponse> parse(@RequestBody AutoInboundParseRequest req,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        try {
            AutoInboundParseResponse resp = autoInboundParseService.parse(req, loginUser.getUser());
            return Result.success(resp);
        } catch (BusinessException e) {
            return Result.error(500, "自动入库解析失败：" + e.getMessage());
        }
    }

    @GetMapping("/{batchId}")
    public Result<AutoInboundParseResponse> getBatch(@PathVariable String batchId) {
        try {
            AutoInboundParseResponse resp = autoInboundParseService.getBatch(batchId);
            return Result.success(resp);
        } catch (BusinessException e) {
            return Result.error(500, "查询自动入库批次失败：" + e.getMessage());
        }
    }

    @PostMapping("/{batchId}/confirm")
    public Result<Void> confirm(@PathVariable String batchId,
                                @RequestBody AutoInboundConfirmRequest request,
                                @AuthenticationPrincipal LoginUser loginUser) {
        try {
            autoInboundConfirmService.confirm(batchId, request, loginUser.getUser());
            return Result.success(null);
        } catch (BusinessException e) {
            return Result.error(500, "自动入库确认失败：" + e.getMessage());
        }
    }
}

