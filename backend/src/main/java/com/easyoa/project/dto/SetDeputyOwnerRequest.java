package com.easyoa.project.dto;

/**
 * 设置项目副负责人（为空表示取消当前副负责人）。
 */
public record SetDeputyOwnerRequest(Long userId) {
}