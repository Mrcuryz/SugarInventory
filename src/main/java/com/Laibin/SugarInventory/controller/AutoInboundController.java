package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.SpringSecurity.LoginUser;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "AI解析自动入库")
@RequestMapping("/api/auto-inbound")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('task:view')")
public class AutoInboundController {

    private final AutoInboundParseService autoInboundParseService;
    private final AutoInboundConfirmService autoInboundConfirmService;

    @Operation(summary = "自动入库文本解析")
    @PostMapping("/parse")
    @PreAuthorize("hasAuthority('task:create')")
    public Result<AutoInboundParseResponse> parse(@RequestBody AutoInboundParseRequest req,
                                                  @AuthenticationPrincipal LoginUser loginUser) {
        AutoInboundParseResponse resp = autoInboundParseService.parse(req, loginUser.getUser());
        return Result.success(resp);
    }

    @Operation(summary = "查询当前缓存的自动入库批次")
    @GetMapping("/history")
    public Result<List<AutoInboundBatchOptionVO>> listHistory(@AuthenticationPrincipal LoginUser loginUser) {
        return Result.success(autoInboundParseService.listBatches(loginUser.getUser()));
    }

    @Operation(summary = "查询自动入库批次")
    @GetMapping("/{batchId}")
    public Result<AutoInboundParseResponse> getBatch(@PathVariable String batchId,
                                                      @AuthenticationPrincipal LoginUser loginUser) {
        AutoInboundParseResponse resp = autoInboundParseService.getBatch(batchId, loginUser.getUser());
        return Result.success(resp);
    }

    @Operation(summary = "自动入库确认")
    @PostMapping("/{batchId}/confirm")
    @PreAuthorize("hasAuthority('task:confirm')")
    public Result<AutoInboundParseResponse> confirm(@PathVariable String batchId,
                                                    @RequestBody AutoInboundConfirmRequest request,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        AutoInboundParseResponse response = autoInboundConfirmService.confirm(batchId, request, loginUser.getUser());
        return Result.success(response);
    }
}

