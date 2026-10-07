package com.easyoa.project.dto;

import java.time.Instant;

import com.easyoa.project.domain.ProjectStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建项目（创建者自动成为 OWNER）。
 */
public record CreateProjectRequest(
        @NotBlank(message = "请输入项目名称")
        @Size(max = 120, message = "项目名称过长")
        String name,

        @Size(max = 2000, message = "项目简介过长")
        String description,

        Instant plannedStartAt,

        Instant plannedEndAt,

        /** 可选的初始成员（创建者自动成为 OWNER，无需重复传入）。 */
        java.util.List<Long> memberUserIds,

        /** 初始进度（默认 0）。 */
        @Min(value = 0, message = "进度不能小于 0")
        @Max(value = 100, message = "进度不能大于 100")
        Integer progress,

        /** 创建时是否直接进入 ACTIVE（默认 DRAFT）。 */
        Boolean activateImmediately,

        /** 创建时的计划状态；为空则按 activateImmediately 推导。 */
        ProjectStatus status) {
}