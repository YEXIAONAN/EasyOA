package com.easyoa.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.system.application.SetupService;
import com.easyoa.system.dto.SetupInitializeRequest;
import com.easyoa.system.dto.SetupResultResponse;
import com.easyoa.system.dto.SetupStatusResponse;

import jakarta.validation.Valid;

/**
 * 首次初始化接口。初始化完成后所有调用都会返回 409。
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public ApiResponse<SetupStatusResponse> status() {
        return ApiResponse.ok(setupService.status());
    }

    @PostMapping("/initialize")
    public ApiResponse<SetupResultResponse> initialize(@Valid @RequestBody SetupInitializeRequest request) {
        return ApiResponse.ok(setupService.initialize(request));
    }
}