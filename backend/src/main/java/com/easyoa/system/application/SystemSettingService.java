package com.easyoa.system.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.system.domain.SystemSetting;
import com.easyoa.system.repository.SystemSettingRepository;

/**
 * 系统设置读写。
 */
@Service
public class SystemSettingService {

    public static final String KEY_SETUP_COMPLETED = "setup.completed";
    public static final String KEY_ORGANIZATION_NAME = "organization.name";

    private static final String VALUE_TRUE = "true";
    private static final String VALUE_FALSE = "false";

    private final SystemSettingRepository systemSettingRepository;

    public SystemSettingService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    @Transactional(readOnly = true)
    public String getValue(String key, String defaultValue) {
        return systemSettingRepository.findById(key)
                .map(SystemSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public boolean isSetupCompleted() {
        return VALUE_TRUE.equalsIgnoreCase(getValue(KEY_SETUP_COMPLETED, VALUE_FALSE));
    }

    @Transactional
    public void setValue(String key, String value, Long actorId) {
        SystemSetting setting = systemSettingRepository.findById(key)
                .orElseGet(() -> new SystemSetting(key, value));
        setting.updateValue(value, actorId);
        systemSettingRepository.save(setting);
    }

    /**
     * 原子标记初始化完成：从 false → true 只有一个请求能成功。
     *
     * @return true 表示本次调用完成了状态切换
     */
    @Transactional
    public boolean markSetupCompleted(Long actorId) {
        int updated = systemSettingRepository.updateValueIfMatches(KEY_SETUP_COMPLETED, VALUE_FALSE, VALUE_TRUE,
                Instant.now(), actorId);
        if (updated == 0) {
            // 设置行缺失（数据被手工清理）时尝试补齐，但仍保持互斥语义
            SystemSetting setting = systemSettingRepository.findById(KEY_SETUP_COMPLETED).orElse(null);
            if (setting == null) {
                systemSettingRepository.save(new SystemSetting(KEY_SETUP_COMPLETED, VALUE_TRUE));
                return true;
            }
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public String organizationName(String defaultValue) {
        return getValue(KEY_ORGANIZATION_NAME, defaultValue);
    }

    @Transactional
    public void requireSetupCompleted() {
        if (!isSetupCompleted()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "系统尚未完成初始化");
        }
    }
}