package com.easyoa.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.system.application.AboutService;
import com.easyoa.system.dto.AboutResponse;

/** Public software/license metadata only; no user, policy, or configuration data. */
@RestController
@RequestMapping("/api/system/about")
public class AboutController {
    private final AboutService service;
    public AboutController(AboutService service) { this.service = service; }
    @GetMapping
    public ApiResponse<AboutResponse> about() { return ApiResponse.ok(service.about()); }
}
