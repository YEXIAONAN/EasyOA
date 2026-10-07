package com.easyoa.security.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.easyoa.common.config.EasyOaProperties;

/**
 * TOTP Secret 的静态加密（AES-256-GCM）。
 *
 * <p>密钥由 {@code easyoa.security.session-secret} 经 SHA-256 派生，
 * 不新增独立密钥配置项；密文格式为 {@code v1:base64(iv|gcmTag|ciphertext)}，
 * 版本前缀为未来密钥轮换预留。
 *
 * <p>安全约束：明文 Secret 只在绑定阶段返回一次，禁止写日志、禁止落库明文。
 */
@Component
public class TotpCipher {

    private static final Logger log = LoggerFactory.getLogger(TotpCipher.class);
    private static final String VERSION = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpCipher(EasyOaProperties properties) {
        this.key = new SecretKeySpec(sha256(properties.getSecurity().getSessionSecret()), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("TOTP Secret 加密失败", ex);
        }
    }

    /**
     * 解密。
     *
     * @return 明文 Secret；密文缺失或无法解密时返回 {@code null}（调用方按「未绑定」处理）
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank()) {
            return null;
        }
        if (!stored.startsWith(VERSION)) {
            log.warn("遇到未知版本的 TOTP 密文，已按未绑定处理");
            return null;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            if (payload.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            log.warn("TOTP Secret 解密失败，已按未绑定处理");
            return null;
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
