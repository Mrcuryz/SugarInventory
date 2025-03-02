package com.Laibin.SugarInventory.util;

import java.security.SecureRandom;
import java.util.Base64;

public class JwtSecretGenerator {

    // 密钥长度
    private static final int SECRET_LENGTH = 64;

    public static void main(String[] args) {
        String jwtSecret = generateJwtSecret();
        System.out.println("Generated JWT Secret: " + jwtSecret);
    }

    // 生成一个符合 JWT 密钥要求的随机密钥
    public static String generateJwtSecret() {
        // 使用 SecureRandom 生成强随机数
        SecureRandom secureRandom = new SecureRandom();

        // 生成指定长度的字节数组
        byte[] secretBytes = new byte[SECRET_LENGTH];
        secureRandom.nextBytes(secretBytes);

        // 将字节数组转换为 Base64 编码的字符串
        return Base64.getEncoder().encodeToString(secretBytes);
    }
}
