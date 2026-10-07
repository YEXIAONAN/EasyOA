package com.easyoa.comment.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论 / 回复。
 *
 * <p>@成员 必须引用项目成员（服务层校验）；附件需为本人已上传到同一任务的文件
 * （服务层将附件归属从任务迁移到该评论）。
 */
public record CreateCommentRequest(
        @NotBlank(message = "评论内容不能为空")
        @Size(max = 4000, message = "评论内容过长")
        String content,

        /** 回复的顶层评论 id；为空表示发表顶层评论。 */
        Long parentId,

        List<Long> mentionUserIds,

        List<Long> attachmentFileIds) {
}