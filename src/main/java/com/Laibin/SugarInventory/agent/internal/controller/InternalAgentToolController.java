package com.Laibin.SugarInventory.agent.internal.controller;

import com.Laibin.SugarInventory.agent.internal.dto.InternalAgentToolRequestDTO;
import com.Laibin.SugarInventory.agent.internal.service.InternalAgentToolGatewayService;
import com.Laibin.SugarInventory.agent.internal.service.impl.McpInternalAgentToolGatewayService;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolResponseVO;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/internal/agent/tools")
public class InternalAgentToolController {
    private final InternalAgentToolGatewayService gatewayService;

    public InternalAgentToolController(InternalAgentToolGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @PostMapping("/{toolName}")
    public ResponseEntity<InternalAgentToolResponseVO> invoke(
            @RequestHeader(value = McpInternalAgentToolGatewayService.SERVICE_KEY_HEADER, required = false) String serviceKey,
            @PathVariable String toolName,
            @RequestBody(required = false) InternalAgentToolRequestDTO request) {
        InternalAgentToolResponseVO response = gatewayService.invoke(serviceKey, toolName, request);
        HttpStatus status = response.getError() != null
                && "SERVICE_AUTHENTICATION_FAILED".equals(response.getError().getCode())
                ? HttpStatus.UNAUTHORIZED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
