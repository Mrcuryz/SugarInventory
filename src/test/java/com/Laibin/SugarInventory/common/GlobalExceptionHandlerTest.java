package com.Laibin.SugarInventory.common;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationFailureDoesNotExposeRejectedValueOrControllerSignature() {
        Object request = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(request, "request");
        bindingResult.addError(new FieldError(
                "request",
                "assistantAnswerSummary",
                "private rejected value",
                false,
                new String[]{"Size.request.assistantAnswerSummary"},
                null,
                "个数必须在0和1000之间"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                mock(org.springframework.core.MethodParameter.class),
                bindingResult);

        Result<Void> result = handler.handleValidationException(exception);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).isEqualTo("请求参数校验失败");
        assertThat(result.getMsg()).doesNotContain("private rejected value", "assistantAnswerSummary");
    }

    @Test
    void unexpectedFailureIsLoggedButReturnsGenericMessage() {
        Result<?> result = handler.handleOtherException(
                new IllegalStateException("internal controller signature must not be exposed"));

        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMsg()).isEqualTo("服务异常，请稍后重试");
        assertThat(result.getMsg()).doesNotContain("jdbc", "secret");
    }
}
