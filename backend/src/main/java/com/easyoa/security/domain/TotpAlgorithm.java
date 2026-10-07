package com.easyoa.security.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * 标准 TOTP 实现（RFC 6238 / RFC 4226）。
 *
 * <p>兼容 Google Authenticator、Microsoft Authenticator、1Password 等标准 App：
 * <ul>
 *   <li>哈希算法 HMAC-SHA1，时间步长 30 秒，6 位数字；</li>
 *   <li>Secret 为 160 bit（20 字节）Base32 编码（RFC 4648，无填充）；</li>
 *   <li>校验时接受前 / 后各一个时间窗（±30s），抵消客户端时钟漂移。</li>
 * </ul>
 *
 * <p>刻意不引入第三方 OTP 库：算法本身稳定且实现规模很小，
 * 自行实现可避免为一个纯函数引入额外依赖与供应链风险。
 */
@Component
public class TotpAlgorithm {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int DIGITS = 6;
    private static final long PERIOD_SECONDS = 30L;
    private static final int DEFAULT_WINDOW = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    /** 生成新的 Base32 Secret（160 bit 熵）。 */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        secureRandom.nextBytes(buffer);
        return base32Encode(buffer);
    }

    /**
     * 校验验证码。
     *
     * @param base32Secret Base32 编码的共享密钥
     * @param code         用户输入的验证码（允许包含空格）
     * @param at           当前时间
     * @return 是否在容差窗口内匹配
     */
    public boolean verify(String base32Secret, String code, Instant at) {
        if (base32Secret == null || code == null) {
            return false;
        }
        String normalized = code.replace(" ", "").trim();
        if (!normalized.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        byte[] key;
        try {
            key = base32Decode(base32Secret);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        long counter = at.getEpochSecond() / PERIOD_SECONDS;
        for (long offset = -DEFAULT_WINDOW; offset <= DEFAULT_WINDOW; offset++) {
            String candidate = hotp(key, counter + offset);
            if (constantTimeEquals(candidate, normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 由 Secret 推导指定时刻的验证码。
     *
     * <p>仅用于服务端诊断与自动化测试（生产校验一律走 {@link #verify}）。
     */
    public String generateCode(String base32Secret, Instant at) {
        return hotp(base32Decode(base32Secret), at.getEpochSecond() / PERIOD_SECONDS);
    }

    /**
     * 构造 {@code otpauth://} URI，供前端渲染二维码。
     *
     * <p>注意：该 URI 内含 Secret 明文，只在绑定阶段返回一次，接口不得再次返回。
     */
    public String otpauthUri(String issuer, String account, String base32Secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + DIGITS
                + "&period=" + PERIOD_SECONDS;
    }

    /** RFC 4226 HOTP：HMAC-SHA1 → 动态截断 → 取模得到 6 位数字。 */
    private String hotp(byte[] key, long counter) {
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP 计算失败：HmacSHA1 不可用", ex);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder builder = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                builder.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            builder.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return builder.toString();
    }

    static byte[] base32Decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("Base32 输入为空");
        }
        String normalized = encoded.trim().replace("=", "").replace(" ", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int index = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("非法 Base32 字符：" + normalized.charAt(i));
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
