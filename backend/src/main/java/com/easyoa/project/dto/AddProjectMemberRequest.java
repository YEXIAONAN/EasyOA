package com.easyoa.project.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 添加项目成员（默认 MEMBER 角色）。
 */
public record AddProjectMemberRequest(@NotNull(message = "请选择成员") Long userId) {
}