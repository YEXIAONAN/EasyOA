package com.easyoa.comment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.comment.application.CommentService;
import com.easyoa.comment.dto.CommentVersionView;
import com.easyoa.comment.dto.CommentView;
import com.easyoa.comment.dto.UpdateCommentRequest;
import com.easyoa.common.response.ApiResponse;

import jakarta.validation.Valid;

/**
 * 评论接口（编辑 / 撤回 / 编辑历史）。
 *
 * <p>编辑与撤回仅作者本人；撤回不做物理删除，原始评论与编辑历史保留供审计追踪。
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PutMapping("/{id}")
    public ApiResponse<CommentView> update(@PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request) {
        return ApiResponse.ok(commentService.update(id, request));
    }

    /** 撤回评论（保留原始内容）。 */
    @PostMapping("/{id}/withdraw")
    public ApiResponse<CommentView> withdraw(@PathVariable Long id) {
        return ApiResponse.ok(commentService.withdraw(id));
    }

    /** 编辑历史（作者 / 项目负责人 / 管理员）。 */
    @GetMapping("/{id}/versions")
    public ApiResponse<List<CommentVersionView>> versions(@PathVariable Long id) {
        return ApiResponse.ok(commentService.versions(id));
    }
}