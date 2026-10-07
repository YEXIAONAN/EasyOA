package com.easyoa.common.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 工具。
 *
 * <p>用途：会话标识签名（数据库只保存 HMAC 值，不保存原始 Session ID）、
 * 未来审计哈希链等。
 */
public final class HmacUtils {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacUtils() {
    }

    /** 计算 HMAC-SHA256，返回小写十六进制字符串（64 字符）。 */
    public static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", ex);
        }
    }

    /** 恒定时间比较，避免时序侧信道。 */
    public static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}