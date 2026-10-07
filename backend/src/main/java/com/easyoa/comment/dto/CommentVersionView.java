package com.easyoa.comment.dto;

import java.time.Instant;

import com.easyoa.comment.domain.CommentVersion;
import com.easyoa.task.dto.TaskUserBrief;

/**
 * 评论历史版本（version_no = 1 为原始内容）。
 */
public record CommentVersionView(
        int versionNo,
        String content,
        TaskUserBrief editor,
        Instant createdAt) {

    public static CommentVersionView from(CommentVersion version) {
        return new CommentVersionView(
                version.getVersionNo(),
                version.getContent(),
                TaskUserBrief.from(version.getEditor()),
                version.getCreatedAt());
    }
}