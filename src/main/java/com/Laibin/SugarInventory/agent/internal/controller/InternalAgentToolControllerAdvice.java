package com.Laibin.SugarInventory.agent.internal.controller;

import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolErrorVO;
import com.Laibin.SugarInventory.agent.internal.vo.InternalAgentToolResponseVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = InternalAgentToolController.class)
public class InternalAgentToolControllerAdvice {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<InternalAgentToolResponseVO> handleUnreadableRequest(HttpMessageNotReadableException ignored) {
        return ResponseEntity.badRequest().body(error(
                "INVALID_ARGUMENT",
                "内部工具请求格式不合法。",
                "request",
                false,
                List.of("检查 JSON 字段和数据类型。")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<InternalAgentToolResponseVO> handleUnexpectedError(Exception ignored) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(
                "INTERNAL_TOOL_GATEWAY_ERROR",
                "内部工具网关暂时无法处理请求。",
                null,
                true,
                List.of("稍后重试或联系管理员。")));
    }

    private InternalAgentToolResponseVO error(String code, String message, String field,
                                              boolean retryable, List<String> suggestedActions) {
        InternalAgentToolErrorVO error = new InternalAgentToolErrorVO(
                code, message, "ERROR", field, retryable, suggestedActions, null);
        return InternalAgentToolResponseVO.error(null, null, error, null);
    }
}
