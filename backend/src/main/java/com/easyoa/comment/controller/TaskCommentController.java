package com.easyoa.comment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.comment.application.CommentService;
import com.easyoa.comment.dto.CommentView;
import com.easyoa.comment.dto.CreateCommentRequest;
import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;

import jakarta.validation.Valid;

/**
 * 任务评论接口（发表 / 列表）。
 *
 * <p>数据范围与任务一致：非项目成员一律 404；发表评论需要项目成员身份。
 */
@RestController
@RequestMapping("/api/tasks/{taskId}/comments")
public class TaskCommentController {

    private final CommentService commentService;

    public TaskCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<PageResponse<CommentView>> list(@PathVariable Long taskId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.ok(commentService.list(taskId, page, Math.min(size, 100)));
    }

    @PostMapping
    public ApiResponse<CommentView> create(@PathVariable Long taskId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.ok(commentService.create(taskId, request));
    }
}