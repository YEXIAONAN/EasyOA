package com.easyoa.security.application;

import java.util.List;
import java.util.Map;

import com.easyoa.security.domain.SensitiveOperationType;
import com.easyoa.security.dto.SensitiveOperationPreviewView;

/**
 * 高危操作基类：统一填充标题 / 说明 / 最终确认短语，子类只提供影响范围与执行逻辑。
 */
public abstract class AbstractSensitiveOperation implements SensitiveOperation {

    @Override
    public SensitiveOperationPreviewView preview(Long targetId, Map<String, Object> payload) {
        SensitiveOperationType type = type();
        return new SensitiveOperationPreviewView(
                type,
                type.displayName(),
                type.description(),
                impacts(targetId, payload),
                type.confirmationPhrase(),
                requiresTarget(),
                targetLabel(targetId, payload));
    }

    /** 影响范围条目（面向操作者的自然语言描述，通常包含受影响记录数）。 */
    protected abstract List<String> impacts(Long targetId, Map<String, Object> payload);

    /** 是否必须指定操作目标。 */
    protected boolean requiresTarget() {
        return false;
    }

    /** 目标展示名（例如被重置 MFA 的账号）。 */
    protected String targetLabel(Long targetId, Map<String, Object> payload) {
        return null;
    }

    protected static int intPayload(Map<String, Object> payload, String key, int defaultValue) {
        if (payload == null) {
            return defaultValue;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    protected static boolean boolPayload(Map<String, Object> payload, String key, boolean defaultValue) {
        if (payload == null) {
            return defaultValue;
        }
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text.trim());
        }
        return defaultValue;
    }
}
