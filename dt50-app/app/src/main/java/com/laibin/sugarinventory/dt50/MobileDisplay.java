package com.laibin.sugarinventory.dt50;

import java.math.BigDecimal;
import java.util.Locale;

final class MobileDisplay {
    private MobileDisplay() {
    }

    static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static String status(String value) {
        if (blankish(value)) {
            return "未知";
        }
        return switch (value.trim()) {
            case "MAINTENANCE", "维护" -> "维护中";
            case "EMPTY", "空置" -> "空置";
            case "FULL", "满仓" -> "满仓";
            case "NORMAL", "正常" -> "正常";
            case "EXPIRING", "临期预警" -> "临期预警";
            case "PREPRINTED" -> "已生成二维码";
            case "USED_UP" -> "已用完";
            case "PENDING" -> "待处理";
            case "IN_STOCK" -> "在库";
            case "FINISHED", "FINISHED_PRODUCT" -> "成品";
            case "SEMI_FINISHED", "SEMI_FINISHED_PRODUCT" -> "半成品";
            default -> looksLikeInternalCode(value) ? "状态待确认" : value.trim();
        };
    }

    static String userMessage(String value) {
        if (blankish(value)) {
            return "操作未完成，请稍后重试";
        }
        return value.trim()
                .replace("PREPRINTED", "已生成二维码")
                .replace("USED_UP", "已用完")
                .replace("IN_STOCK", "在库")
                .replace("PENDING", "待处理")
                .replace("托盘码", "二维码")
                .replace("托盘", "二维码");
    }

    static String errorMessage(String value, int httpStatus) {
        if (httpStatus == 401) return "登录状态已失效，请重新登录";
        if (httpStatus == 403) return "当前账号无权使用此功能";
        if (httpStatus == 409 || httpStatus == 1032) return "数据已被其他人更新，请核对后重试";
        if (httpStatus == 429) return "操作过于频繁，请稍后重试";
        if (httpStatus >= 500) return "服务暂时不可用，请稍后重试";
        if (blankish(value)) return "操作未完成，请稍后重试";

        String message = value.trim();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("jwt") || lower.contains("token") || lower.contains("unauthorized")) {
            return "登录状态已失效，请重新登录";
        }
        if (lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("connection") || lower.contains("unable to resolve host")) {
            return "网络连接异常，请检查网络后重试";
        }
        if (message.length() > 160 || message.contains("\n") || message.contains("\r")
                || lower.contains("json") || lower.contains("exception")
                || lower.contains("java.") || lower.contains("org.")
                || lower.contains("sql") || lower.contains("http")
                || lower.contains("stack trace") || lower.contains("traceback")
                || lower.contains("cannot be converted")) {
            return "操作未完成，请稍后重试或联系管理员";
        }
        if (!message.matches("(?s).*[\\u4E00-\\u9FFF].*")) {
            return "操作未完成，请稍后重试或联系管理员";
        }
        return userMessage(message);
    }

    static String operation(String value) {
        if (blankish(value)) {
            return "仓库操作";
        }
        return switch (value.trim()) {
            case "IN", "INBOUND", "入库" -> "入库";
            case "OUT", "OUTBOUND", "出库" -> "出库";
            case "TRANSFER", "MOVE", "调拨" -> "调拨";
            default -> looksLikeInternalCode(value) ? "仓库操作" : value.trim();
        };
    }

    static String dateTime(String value) {
        if (blankish(value)) {
            return "时间未记录";
        }
        String normalized = value.trim().replace('T', ' ');
        return normalized.length() > 19 ? normalized.substring(0, 19) : normalized;
    }

    static String fallback(String value, String fallback) {
        return blankish(value) ? fallback : value.trim();
    }

    static String occupancy(double current, double maximum) {
        if (maximum <= 0 || current <= 0) {
            return "0%";
        }
        double percentage = current * 100 / maximum;
        if (percentage < 1) {
            return "<1%";
        }
        return Math.round(Math.min(100, percentage)) + "%";
    }

    private static boolean blankish(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }

    private static boolean looksLikeInternalCode(String value) {
        return value != null && value.trim().matches("[A-Z][A-Z0-9_]*");
    }
}
