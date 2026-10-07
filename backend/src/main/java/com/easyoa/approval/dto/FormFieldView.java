package com.easyoa.approval.dto;

import java.util.List;

/**
 * 审批表单字段定义（模板 schema 与实例快照共用）。
 *
 * <p>支持字段类型：TEXT / TEXTAREA / NUMBER / MONEY / DATE / DATETIME /
 * SELECT / MULTI_SELECT / USER / ATTACHMENT。
 */
public record FormFieldView(
        String key,
        String label,
        String type,
        boolean required,
        List<String> options,
        String placeholder) {
}