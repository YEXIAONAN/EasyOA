package com.easyoa.approval.dto;

import java.time.Instant;

import com.easyoa.approval.domain.ApprovalTemplate;

/**
 * 审批模板列表卡片。
 */
public record TemplateSummaryView(
        Long id,
        String name,
        String description,
        boolean enabled,
        int latestVersionNo,
        Instant updatedAt) {

    public static TemplateSummaryView from(ApprovalTemplate template) {
        return new TemplateSummaryView(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.isEnabled(),
                template.getLatestVersionNo(),
                template.getUpdatedAt());
    }
}