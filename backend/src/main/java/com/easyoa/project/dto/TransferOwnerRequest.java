package com.easyoa.project.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 转让项目 OWNER（目标必须是项目成员；转让后原 OWNER 变为普通成员）。
 */
public record TransferOwnerRequest(@NotNull(message = "请选择新的项目负责人") Long userId) {
}