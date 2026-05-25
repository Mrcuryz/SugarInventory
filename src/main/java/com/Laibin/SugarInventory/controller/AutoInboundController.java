package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
import com.Laibin.SugarInventory.common.BusinessException;
import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.AutoInboundConfirmRequest;
import com.Laibin.SugarInventory.domain.dto.AutoInboundParseRequest;
import com.Laibin.SugarInventory.domain.vo.AutoInboundBatchOptionVO;
import com.Laibin.SugarInventory.domain.vo.AutoInboundParseResponse;
import com.Laibin.SugarInventory.service.AutoInboundConfirmService;
import com.Laibin.SugarInventory.service.AutoInboundParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "AI解析自动入库")
@RequestMapping("/api/auto-inbound")
@RequiredArgsConstructor
public class AutoInboundController {

    private final AutoInboundParseService autoInboundParseService;
    private final AutoInboundConfirmService autoInboundConfirmService;

    @Operation(summary = "自动入库文本解析")
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

    @Operation(summary = "查询当前缓存的自动入库批次")
    @GetMapping("/history")
    public Result<List<AutoInboundBatchOptionVO>> listHistory(@AuthenticationPrincipal LoginUser loginUser) {
        try {
            return Result.success(autoInboundParseService.listBatches(loginUser.getUser()));
        } catch (BusinessException e) {
            return Result.error(500, "查询自动入库历史失败：" + e.getMessage());
        }
    }

    @Operation(summary = "查询自动入库批次")
    @GetMapping("/{batchId}")
    public Result<AutoInboundParseResponse> getBatch(@PathVariable String batchId) {
        try {
            AutoInboundParseResponse resp = autoInboundParseService.getBatch(batchId);
            return Result.success(resp);
        } catch (BusinessException e) {
            return Result.error(500, "查询自动入库批次失败：" + e.getMessage());
        }
    }

    @Operation(summary = "自动入库确认")
    @PostMapping("/{batchId}/confirm")
    public Result<AutoInboundParseResponse> confirm(@PathVariable String batchId,
                                                    @RequestBody AutoInboundConfirmRequest request,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        try {
            AutoInboundParseResponse response = autoInboundConfirmService.confirm(batchId, request, loginUser.getUser());
            return Result.success(response);
        } catch (BusinessException e) {
            return Result.error(500, "自动入库确认失败：" + e.getMessage());
        }
    }
}

