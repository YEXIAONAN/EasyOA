package com.easyoa.security.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.security.application.MfaService;
import com.easyoa.security.application.SecuritySettingsService;
import com.easyoa.security.application.SensitiveOperationService;
import com.easyoa.security.dto.ConfirmMfaRequest;
import com.easyoa.security.dto.DisableMfaRequest;
import com.easyoa.security.dto.MfaEnrollmentView;
import com.easyoa.security.dto.MfaStatusView;
import com.easyoa.security.dto.SecurityPolicyView;
import com.easyoa.security.dto.SensitiveOperationPreviewRequest;
import com.easyoa.security.dto.SensitiveOperationPreviewView;
import com.easyoa.security.dto.SensitiveOperationRequest;
import com.easyoa.security.dto.SensitiveOperationResultView;

import jakarta.validation.Valid;

/**
 * 安全中心接口。
 *
 * <p>分为三类：
 * <ul>
 *   <li>动态口令自助管理（任何登录用户）；</li>
 *   <li>安全策略读取（ROOT / ADMIN 只读）；</li>
 *   <li>高危操作预览与执行（仅 ROOT，认证仪式在应用层统一实现）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final MfaService mfaService;
    private final SecuritySettingsService securitySettingsService;
    private final SensitiveOperationService sensitiveOperationService;

    public SecurityController(MfaService mfaService, SecuritySettingsService securitySettingsService,
            SensitiveOperationService sensitiveOperationService) {
        this.mfaService = mfaService;
        this.securitySettingsService = securitySettingsService;
        this.sensitiveOperationService = sensitiveOperationService;
    }

    // --- 动态口令（自助） ---

    @GetMapping("/mfa")
    public ApiResponse<MfaStatusView> mfaStatus(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(mfaService.status(principal.id()));
    }

    @PostMapping("/mfa/enrollment")
    public ApiResponse<MfaEnrollmentView> startEnrollment(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(mfaService.startEnrollment(principal.id()));
    }

    @PostMapping("/mfa/enrollment/confirm")
    public ApiResponse<MfaStatusView> confirmEnrollment(@AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody ConfirmMfaRequest request) {
        return ApiResponse.ok(mfaService.confirmEnrollment(principal.id(), request.code()));
    }

    @DeleteMapping("/mfa/enrollment")
    public ApiResponse<Void> cancelEnrollment(@AuthenticationPrincipal SecurityUser principal) {
        mfaService.cancelEnrollment(principal.id());
        return ApiResponse.ok();
    }

    @PostMapping("/mfa/disable")
    public ApiResponse<MfaStatusView> disableMfa(@AuthenticationPrincipal SecurityUser principal,
            @Valid @RequestBody DisableMfaRequest request) {
        return ApiResponse.ok(mfaService.disable(principal.id(), request.currentPassword(), request.code()));
    }

    // --- 安全策略（只读） ---

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ROOT', 'ADMIN')")
    public ApiResponse<SecurityPolicyView> securityPolicy() {
        return ApiResponse.ok(securitySettingsService.current());
    }

    // --- 高危操作（仅 ROOT） ---

    @PostMapping("/sensitive-operations/preview")
    public ApiResponse<SensitiveOperationPreviewView> previewSensitiveOperation(
            @Valid @RequestBody SensitiveOperationPreviewRequest request) {
        return ApiResponse.ok(sensitiveOperationService.preview(request));
    }

    @PostMapping("/sensitive-operations/execute")
    public ApiResponse<SensitiveOperationResultView> executeSensitiveOperation(
            @Valid @RequestBody SensitiveOperationRequest request) {
        return ApiResponse.ok(sensitiveOperationService.execute(request));
    }
}
