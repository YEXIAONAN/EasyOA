package com.easyoa.approval.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.approval.application.ApprovalTemplateService;
import com.easyoa.approval.dto.CreateTemplateRequest;
import com.easyoa.approval.dto.PublishTemplateVersionRequest;
import com.easyoa.approval.dto.TemplateDetailView;
import com.easyoa.approval.dto.TemplateSummaryView;
import com.easyoa.approval.dto.UpdateTemplateRequest;
import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;

import jakarta.validation.Valid;

/**
 * 审批模板接口。
 *
 * <p>普通用户只能看到启用模板（用于发起申请）；创建 / 编辑 / 发布版本仅系统管理员。
 * 模板内容版本化：修改必须发布新版本，已运行实例继续使用旧版本。
 */
@RestController
@RequestMapping("/api/approval-templates")
public class ApprovalTemplateController {

    private final ApprovalTemplateService templateService;

    public ApprovalTemplateController(ApprovalTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<PageResponse<TemplateSummaryView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return ApiResponse.ok(templateService.list(keyword, page, Math.min(size, 100)));
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(templateService.detail(id));
    }

    @PostMapping
    public ApiResponse<TemplateDetailView> create(@Valid @RequestBody CreateTemplateRequest request) {
        return ApiResponse.ok(templateService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateDetailView> update(@PathVariable Long id,
            @Valid @RequestBody UpdateTemplateRequest request) {
        return ApiResponse.ok(templateService.update(id, request));
    }

    /** 发布新版本（表单与节点整体替换）。 */
    @PostMapping("/{id}/versions")
    public ApiResponse<TemplateDetailView> publishVersion(@PathVariable Long id,
            @Valid @RequestBody PublishTemplateVersionRequest request) {
        return ApiResponse.ok(templateService.publishVersion(id, request));
    }
}