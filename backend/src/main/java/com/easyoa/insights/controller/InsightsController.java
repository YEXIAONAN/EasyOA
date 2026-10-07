package com.easyoa.insights.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.insights.application.InsightsService;
import com.easyoa.insights.dto.InsightsResponse;

/**
 * 数据中心接口（所有登录用户；数据范围由服务层按角色收敛）。
 */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping
    public ApiResponse<InsightsResponse> overview() {
        return ApiResponse.ok(insightsService.overview());
    }
}
