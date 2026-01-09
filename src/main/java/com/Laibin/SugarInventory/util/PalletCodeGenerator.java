package com.Laibin.SugarInventory.util;

import com.Laibin.SugarInventory.domain.po.Assay;
import lombok.Data;

import java.time.LocalDate;

/**
 * 托盘码编码工具。
 * 设计思路：
 * - 正文使用 LCG 生成的 5 位 Base36 全排列（容量 36^5），避免暴露自增 ID 并保持唯一。
 * - 校验位对“BT+正文”做 Base36 求和取模，快速发现误扫/误输。
 * - 如需扩容到 6 位，只需调整 BODY_LENGTH 与 MODULUS。
 */
public class PalletCodeGenerator {
    private static final String BASE36_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final String PREFIX = "BT";
    private static final int BODY_LENGTH = 5;
    private static final long MODULUS = (long) Math.pow(36, BODY_LENGTH);
    private static final long MULTIPLIER = 13L; // 满足全周期
    private static final long INCREMENT = 5L;

    private PalletCodeGenerator() {
    }

    /**
     * 把自增 id 转成不带符号的 base36 字符串，大写
     * (旧接口保留，当前生成逻辑不依赖 base36 主体)
     */
    public static String toBase36(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
        return Long.toString(id, 36).toUpperCase();
    }

    /**
     * 计算校验位，基于 Base36
     */
    public static char calcCheckChar(String body) {
        if (body == null || body.isEmpty()) {
            throw new IllegalArgumentException("body cannot be empty");
        }
        int sum = 0;
        for (char c : body.toUpperCase().toCharArray()) {
            int value = BASE36_CHARSET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("invalid base36 char: " + c);
            }
            sum += value;
        }
        return BASE36_CHARSET.charAt(sum % BASE36_CHARSET.length());
    }

    /**
     * 生成完整托盘码：BT + 5 位base36（线性同余打散 id）+ 校验位
     */
    public static String generateCode(long id) {
        String body = generateBody(id);
        String withoutCheck = PREFIX + body;
        char checkChar = calcCheckChar(withoutCheck);
        return withoutCheck + checkChar;
    }

    /**
     * 基本格式校验：BT + 5位Base36 + 1位[0-9A-Z]
     */
    public static boolean isValidFormat(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.trim().toUpperCase();
        if (normalized.length() != PREFIX.length() + BODY_LENGTH + 1) {
            return false;
        }
        if (!normalized.startsWith(PREFIX)) {
            return false;
        }
        String body = normalized.substring(PREFIX.length(), PREFIX.length() + BODY_LENGTH);
        if (!body.matches("[0-9A-Z]{" + BODY_LENGTH + "}")) {
            return false;
        }
        char last = normalized.charAt(normalized.length() - 1);
        return last >= '0' && last <= '9' || last >= 'A' && last <= 'Z';
    }

    /**
     * 校验位校验：根据前缀+数字部分重新计算校验位，并与末位字符比较
     */
    public static boolean verifyCheckChar(String code) {
        if (!isValidFormat(code)) {
            return false;
        }
        String normalized = code.trim().toUpperCase();
        String prefixAndBody = normalized.substring(0, PREFIX.length() + BODY_LENGTH);
        char checkChar = normalized.charAt(normalized.length() - 1);
        return calcCheckChar(prefixAndBody) == checkChar;
    }

    private static String generateBody(long id) {
        // LCG: (id * A + C) mod 36^5，生成对 id 的一一映射的“伪随机”正文
        long mixed = (id * MULTIPLIER + INCREMENT) % MODULUS;
        if (mixed < 0) {
            mixed += MODULUS;
        }
        String base36 = Long.toString(mixed, 36).toUpperCase();
        return String.format("%" + BODY_LENGTH + "s", base36).replace(' ', '0');
    }
}
