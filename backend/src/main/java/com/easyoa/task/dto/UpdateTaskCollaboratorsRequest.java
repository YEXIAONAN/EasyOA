package com.easyoa.task.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 设置协作成员（整体替换语义；传空数组表示清空）。
 */
public record UpdateTaskCollaboratorsRequest(
        @Size(max = 50, message = "协作成员数量过多")
        List<Long> userIds) {
}