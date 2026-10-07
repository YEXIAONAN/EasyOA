package com.easyoa.approval.dto;

import java.time.Instant;

/**
 * 审批模板详情（含当前最新版本定义）。
 */
public record TemplateDetailView(
        Long id,
        String name,
        String description,
        boolean enabled,
        int latestVersionNo,
        Instant updatedAt,
        TemplateVersionView latestVersion) {
}