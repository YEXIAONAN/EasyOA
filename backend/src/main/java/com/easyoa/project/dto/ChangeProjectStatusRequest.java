package com.easyoa.project.dto;

import com.easyoa.project.domain.ProjectStatus;

import jakarta.validation.constraints.NotNull;

/**
 * 变更项目状态（生命周期流转由后端校验）。
 */
public record ChangeProjectStatusRequest(@NotNull(message = "请选择项目状态") ProjectStatus status) {
}