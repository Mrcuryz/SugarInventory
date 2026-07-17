package com.Laibin.SugarInventory.controller;

import com.Laibin.SugarInventory.common.Result;
import com.Laibin.SugarInventory.domain.dto.FixedProductQrPoolAgentQueryDTO;
import com.Laibin.SugarInventory.domain.vo.FixedProductQrPoolAgentVO;
import com.Laibin.SugarInventory.service.FixedProductQrPoolAgentReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pallet-codes/agent-read")
@RequiredArgsConstructor
public class FixedProductQrPoolAgentReadController {
    private final FixedProductQrPoolAgentReadService service;

    @PostMapping("/fixed-product-pool/query")
    @PreAuthorize("hasAuthority('qrcode:pool_view')")
    public Result<FixedProductQrPoolAgentVO> queryFixedProductQrPool(@RequestBody(required = false) FixedProductQrPoolAgentQueryDTO query) {
        return Result.success(service.queryFixedProductQrPool(query));
    }
}
