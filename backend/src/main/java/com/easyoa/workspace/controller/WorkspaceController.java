package com.easyoa.workspace.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.workspace.application.WorkspaceService;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse;

/**
 * 工作台（首页）接口。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/summary")
    public ApiResponse<WorkspaceSummaryResponse> summary(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(workspaceService.summary(principal.id()));
    }
}