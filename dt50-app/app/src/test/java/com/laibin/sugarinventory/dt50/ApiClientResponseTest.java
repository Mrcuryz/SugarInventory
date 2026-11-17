package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ApiClientResponseTest {
    @Test
    public void plainTextExpiredJwtPreservesUnauthorizedStatus() {
        ApiClient.ApiFailure failure = ApiClient.nonJsonResponseFailure(401);
        assertEquals(401, failure.httpStatus);
        assertEquals("登录状态已失效，请重新登录", failure.getMessage());
    }

    @Test
    public void plainTextForbiddenDoesNotExposeServerResponse() {
        ApiClient.ApiFailure failure = ApiClient.nonJsonResponseFailure(403);
        assertEquals(403, failure.httpStatus);
        assertEquals("当前账号无权使用此功能", failure.getMessage());
    }

    @Test
    public void malformedSuccessResponseIsReportedAsFormatError() {
        ApiClient.ApiFailure failure = ApiClient.nonJsonResponseFailure(200);
        assertEquals(200, failure.httpStatus);
        assertEquals("服务器返回的数据格式不正确", failure.getMessage());
    }
}
