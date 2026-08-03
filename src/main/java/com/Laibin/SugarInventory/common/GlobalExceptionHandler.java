package com.Laibin.SugarInventory.common;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Request validation failed with {} field error(s)", e.getBindingResult().getFieldErrorCount());
        return Result.error(400, "请求参数校验失败");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleOtherException(Exception e) {
        log.error("Unhandled request exception", e);
        return Result.error("服务异常，请稍后重试");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        return Result.error(403, "权限不足");
    }
}
