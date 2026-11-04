package com.laibin.sugarinventory.dt50;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MobileDisplayTest {
    @Test
    public void mapsInternalWarehouseStatusesToUserLanguage() {
        assertEquals("维护中", MobileDisplay.status("MAINTENANCE"));
        assertEquals("空置", MobileDisplay.status("EMPTY"));
        assertEquals("满仓", MobileDisplay.status("FULL"));
        assertEquals("正常", MobileDisplay.status("NORMAL"));
        assertEquals("已生成二维码", MobileDisplay.status("PREPRINTED"));
        assertEquals("已用完", MobileDisplay.status("USED_UP"));
        assertEquals("状态待确认", MobileDisplay.status("UNEXPECTED_INTERNAL_STATUS"));
    }

    @Test
    public void replacesBackendTermsBeforeShowingMessages() {
        assertEquals("二维码 QR001 当前为已用完",
                MobileDisplay.userMessage("托盘 QR001 当前为USED_UP"));
        assertEquals("二维码不存在", MobileDisplay.userMessage("托盘码不存在"));
        assertEquals("入库", MobileDisplay.operation("INBOUND"));
        assertEquals("仓库操作", MobileDisplay.operation("UNKNOWN_EVENT"));
    }

    @Test
    public void technicalErrorsNeverReachTheUser() {
        assertEquals("登录状态已失效，请重新登录",
                MobileDisplay.errorMessage("Value JWT of type java.lang.String cannot be converted to JSONObject", 0));
        assertEquals("登录状态已失效，请重新登录",
                MobileDisplay.errorMessage("JWT Token已过期", 401));
        assertEquals("当前账号无权使用此功能",
                MobileDisplay.errorMessage("Forbidden", 403));
        assertEquals("网络连接异常，请检查网络后重试",
                MobileDisplay.errorMessage("Connection timed out", 0));
        assertEquals("服务暂时不可用，请稍后重试",
                MobileDisplay.errorMessage("SQLException: internal details", 500));
        assertEquals("操作未完成，请稍后重试或联系管理员",
                MobileDisplay.errorMessage("org.json.JSONException: invalid response", 0));
        assertEquals("二维码不存在",
                MobileDisplay.errorMessage("托盘码不存在", 404));
    }

    @Test
    public void formatsNumbersAndIsoTimeWithoutNoise() {
        assertEquals("12", MobileDisplay.number(12.0));
        assertEquals("12.5", MobileDisplay.number(12.5));
        assertEquals("2026-08-30 14:25:11", MobileDisplay.dateTime("2026-08-30T14:25:11"));
        assertEquals("无", MobileDisplay.fallback("null", "无"));
        assertEquals("<1%", MobileDisplay.occupancy(27, 13340));
    }
}
