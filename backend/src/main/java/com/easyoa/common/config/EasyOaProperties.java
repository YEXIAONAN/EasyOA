package com.easyoa.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EasyOA 应用配置（前缀 {@code easyoa}）。
 *
 * <p>所有敏感配置通过环境变量注入，不从代码或仓库中读取默认密钥。
 * 生产环境缺失必填配置时应直接启动失败，而不是降级运行。
 */
@Validated
@ConfigurationProperties(prefix = "easyoa")
public class EasyOaProperties {

    private final Security security = new Security();
    private final Setup setup = new Setup();
    private final Storage storage = new Storage();
    private final DevSeed devSeed = new DevSeed();

    public Security getSecurity() {
        return security;
    }

    public Setup getSetup() {
        return setup;
    }

    public Storage getStorage() {
        return storage;
    }

    public DevSeed getDevSeed() {
        return devSeed;
    }

    /** 开发环境演示数据开关（仅 dev profile 生效；生产环境不读取）。 */
    public static class DevSeed {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Security {

        /**
         * 会话安全主密钥：用于 HMAC 签名会话标识（数据库只保存签名值），
         * 未来用于加密 TOTP Secret 等敏感字段。
         */
        @NotBlank
        @Size(min = 32, message = "security.session-secret 长度必须不少于 32 字符")
        private String sessionSecret;

        /** 会话有效期，与 server.servlet.session.timeout 保持一致。 */
        private Duration sessionTimeout = Duration.ofMinutes(480);

        /** 连续登录失败次数上限，超过后临时锁定。 */
        @Min(1)
        private int loginMaxFailures = 5;

        /** 临时锁定时长（分钟）。 */
        @Min(1)
        private int loginLockMinutes = 15;

        public String getSessionSecret() {
            return sessionSecret;
        }

        public void setSessionSecret(String sessionSecret) {
            this.sessionSecret = sessionSecret;
        }

        public Duration getSessionTimeout() {
            return sessionTimeout;
        }

        public void setSessionTimeout(Duration sessionTimeout) {
            this.sessionTimeout = sessionTimeout;
        }

        public int getLoginMaxFailures() {
            return loginMaxFailures;
        }

        public void setLoginMaxFailures(int loginMaxFailures) {
            this.loginMaxFailures = loginMaxFailures;
        }

        public int getLoginLockMinutes() {
            return loginLockMinutes;
        }

        public void setLoginLockMinutes(int loginLockMinutes) {
            this.loginLockMinutes = loginLockMinutes;
        }
    }

    public static class Setup {

        /** 首次初始化页面默认组织名称。 */
        private String organizationName = "Easy Studio";

        public String getOrganizationName() {
            return organizationName;
        }

        public void setOrganizationName(String organizationName) {
            this.organizationName = organizationName;
        }
    }

    public static class Storage {

        /** 附件存储根目录（UUID 命名，不以原始文件名落盘）。 */
        private String path = "/var/lib/easyoa/storage";

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}