package com.easyoa.workspace.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.workspace.application.ActivityService;
import com.easyoa.workspace.application.WorkspaceService;
import com.easyoa.workspace.dto.ActivityView;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse;

/**
 * 工作台（首页）接口。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final ActivityService activityService;

    public WorkspaceController(WorkspaceService workspaceService, ActivityService activityService) {
        this.workspaceService = workspaceService;
        this.activityService = activityService;
    }

    @GetMapping("/summary")
    public ApiResponse<WorkspaceSummaryResponse> summary(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(workspaceService.summary(principal.id()));
    }

    /** Activity Feed（业务动态，含深链；数据范围见 ActivityService）。 */
    @GetMapping("/activity")
    public ApiResponse<List<ActivityView>> activity(@AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ApiResponse.ok(activityService.recent(principal.id(), Math.min(Math.max(limit, 1), 30)));
    }
}