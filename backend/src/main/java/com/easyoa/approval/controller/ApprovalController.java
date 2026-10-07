package com.easyoa.approval.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.easyoa.approval.application.ApprovalService;
import com.easyoa.approval.dto.ApprovalCardView;
import com.easyoa.approval.dto.ApprovalDetailView;
import com.easyoa.approval.dto.ReviewApprovalRequest;
import com.easyoa.approval.dto.SubmitApprovalRequest;
import com.easyoa.approval.dto.TransferApprovalRequest;
import com.easyoa.approval.dto.UpdateApprovalFormRequest;
import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.file.application.FileService;
import com.easyoa.file.dto.FileView;
import com.easyoa.approval.application.ApprovalPermissionService;

import jakarta.validation.Valid;

/**
 * 审批实例接口。
 *
 * <p>数据范围：申请人、参与过审批的人与系统管理员可见；其余一律 404。
 * 同意 / 拒绝 / 退回仅当前节点待审批人；撤回仅申请人；转交仅管理员（完整审计）。
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalPermissionService permissionService;
    private final FileService fileService;

    public ApprovalController(ApprovalService approvalService, ApprovalPermissionService permissionService,
            FileService fileService) {
        this.approvalService = approvalService;
        this.permissionService = permissionService;
        this.fileService = fileService;
    }

    /** scope：PENDING（待我审批）/ MINE（我发起的）/ FINISHED（已完成）。 */
    @GetMapping
    public ApiResponse<PageResponse<ApprovalCardView>> list(
            @RequestParam(required = false, defaultValue = "PENDING") String scope,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "15") Integer size) {
        SecurityUser actor = permissionService.requireAuthenticated();
        return ApiResponse.ok(approvalService.list(scope, actor.id(), page, Math.min(size, 100)));
    }

    /** 发起审批（创建草稿）。 */
    @PostMapping
    public ApiResponse<ApprovalDetailView> create(@Valid @RequestBody SubmitApprovalRequest request) {
        return ApiResponse.ok(approvalService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ApprovalDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.detail(id));
    }

    @PutMapping("/{id}/form")
    public ApiResponse<ApprovalDetailView> updateForm(@PathVariable Long id,
            @Valid @RequestBody UpdateApprovalFormRequest request) {
        return ApiResponse.ok(approvalService.updateForm(id, request));
    }

    /** 提交（解析动态审批人并生成快照；无法解析出合法审批人则拒绝）。 */
    @PostMapping("/{id}/submit")
    public ApiResponse<ApprovalDetailView> submit(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ApprovalDetailView> approve(@PathVariable Long id,
            @Valid @RequestBody(required = false) ReviewApprovalRequest request) {
        return ApiResponse.ok(approvalService.approve(id, request == null ? null : request.comment()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ApprovalDetailView> reject(@PathVariable Long id,
            @Valid @RequestBody ReviewApprovalRequest request) {
        return ApiResponse.ok(approvalService.reject(id, request.comment()));
    }

    @PostMapping("/{id}/return")
    public ApiResponse<ApprovalDetailView> returnForRevision(@PathVariable Long id,
            @Valid @RequestBody ReviewApprovalRequest request) {
        return ApiResponse.ok(approvalService.returnForRevision(id, request.comment()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<ApprovalDetailView> withdraw(@PathVariable Long id) {
        return ApiResponse.ok(approvalService.withdraw(id));
    }

    /** 转交（仅系统管理员）。 */
    @PostMapping("/{id}/transfer")
    public ApiResponse<ApprovalDetailView> transfer(@PathVariable Long id,
            @Valid @RequestBody TransferApprovalRequest request) {
        return ApiResponse.ok(approvalService.transfer(id, request));
    }

    /** 审批表单附件上传（草稿阶段；提交时挂载到实例）。 */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileView> uploadAttachment(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(fileService.uploadApprovalAttachment(file));
    }
}