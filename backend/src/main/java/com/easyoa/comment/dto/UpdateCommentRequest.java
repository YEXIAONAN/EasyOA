package com.easyoa.comment.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑评论（仅作者本人；每次编辑保留完整历史）。
 */
public record UpdateCommentRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 4000, message = "评论内容过长")
        String content,

        /** 提供时整体替换 @成员（不提供则保持不变）。 */
        List<Long> mentionUserIds) {
}