package com.easyoa.comment.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.file.dto.FileView;
import com.easyoa.task.dto.TaskUserBrief;

/**
 * 评论视图（顶层评论包含其一级回复）。
 *
 * <p>撤回的评论：{@code withdrawn = true}，内容 / @成员 / 附件不再返回（界面显示
 * 「某某 撤回了一条评论」），数据库仍保留原始内容与编辑历史供审计追踪。
 */
public record CommentView(
        Long id,
        Long taskId,
        Long parentId,
        TaskUserBrief author,
        String content,
        boolean withdrawn,
        boolean edited,
        List<TaskUserBrief> mentions,
        List<FileView> attachments,
        List<CommentView> replies,
        Instant createdAt,
        Instant updatedAt) {
}