package com.easyoa.approval.application;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.domain.ApprovalTemplate;
import com.easyoa.approval.domain.ApprovalTemplateVersion;
import com.easyoa.approval.dto.CreateTemplateRequest;
import com.easyoa.approval.dto.PublishTemplateVersionRequest;
import com.easyoa.approval.dto.TemplateDetailView;
import com.easyoa.approval.dto.TemplateSummaryView;
import com.easyoa.approval.dto.TemplateVersionView;
import com.easyoa.approval.dto.UpdateTemplateRequest;
import com.easyoa.approval.repository.ApprovalTemplateRepository;
import com.easyoa.approval.repository.ApprovalTemplateVersionRepository;
import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;

/**
 * 审批模板服务：模板 CRUD 与版本发布。
 *
 * <p>版本语义：模板内容（表单 + 节点）不可原地修改，只能发布新版本；
 * 新申请使用最新版本，已运行实例继续使用发起时版本（历史审批不受影响）。
 */
@Service
public class ApprovalTemplateService {

    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalTemplateVersionRepository versionRepository;
    private final ApprovalSchemaCodec schemaCodec;
    private final ApprovalPermissionService permissionService;
    private final AuditService auditService;

    public ApprovalTemplateService(ApprovalTemplateRepository templateRepository,
            ApprovalTemplateVersionRepository versionRepository, ApprovalSchemaCodec schemaCodec,
            ApprovalPermissionService permissionService, AuditService auditService) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.schemaCodec = schemaCodec;
        this.permissionService = permissionService;
        this.auditService = auditService;
    }

    // --- 查询 -------------------------------------------------------------------

    /** 模板列表：普通用户只看启用模板；管理员可看到全部。 */
    @Transactional(readOnly = true)
    public PageResponse<TemplateSummaryView> list(String keyword, int page, int size) {
        SecurityUser actor = permissionService.requireAuthenticated();
        String normalized = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        Page<ApprovalTemplate> result = templateRepository.search(permissionService.isAdminLike(actor), normalized,
                PageRequest.of(Math.max(page, 1) - 1, Math.max(size, 1)));
        return PageResponse.from(result, TemplateSummaryView::from);
    }

    @Transactional(readOnly = true)
    public TemplateDetailView detail(Long templateId) {
        SecurityUser actor = permissionService.requireAuthenticated();
        ApprovalTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("审批模板不存在或无权访问"));
        if (!template.isEnabled() && !permissionService.isAdminLike(actor)) {
            throw ApiException.notFound("审批模板不存在或无权访问");
        }
        return toDetail(template);
    }

    // --- 管理（仅系统管理员） -------------------------------------------------------

    @Transactional
    public TemplateDetailView create(CreateTemplateRequest request) {
        SecurityUser actor = permissionService.requireAdminLike();
        String name = request.name().trim();
        templateRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw ApiException.conflict("已存在同名审批模板：" + name);
        });
        schemaCodec.validateSchema(request.formFields(), request.nodes());

        ApprovalTemplate template = new ApprovalTemplate(name, normalize(request.description()), actor.id());
        templateRepository.save(template);
        ApprovalTemplateVersion version = publish(template, 1, request.formFields(), request.nodes(), actor);
        template.publishVersion(version.getVersionNo());
        templateRepository.save(template);

        auditService.record(AuditEntry.action(AuditActions.APPROVAL_TEMPLATE_CREATED, RiskLevel.ELEVATED)
                .resource("APPROVAL_TEMPLATE", template.getId())
                .after(java.util.Map.of("name", name, "version", version.getVersionNo(),
                        "nodes", request.nodes().size()))
                .reason("创建审批模板并发布 v1"));
        return toDetail(template);
    }

    @Transactional
    public TemplateDetailView update(Long templateId, UpdateTemplateRequest request) {
        permissionService.requireAdminLike();
        ApprovalTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("审批模板不存在"));
        String name = request.name().trim();
        templateRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(templateId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("已存在同名审批模板：" + name);
                });
        template.updateInfo(name, normalize(request.description()));
        if (request.enabled() != null) {
            template.changeEnabled(request.enabled());
        }
        templateRepository.save(template);
        return toDetail(template);
    }

    /** 发布新版本（表单与节点整体替换；不影响已运行实例）。 */
    @Transactional
    public TemplateDetailView publishVersion(Long templateId, PublishTemplateVersionRequest request) {
        SecurityUser actor = permissionService.requireAdminLike();
        ApprovalTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("审批模板不存在"));
        schemaCodec.validateSchema(request.formFields(), request.nodes());

        int nextVersion = versionRepository.maxVersionNo(templateId) + 1;
        ApprovalTemplateVersion version = publish(template, nextVersion, request.formFields(), request.nodes(),
                actor);
        template.publishVersion(version.getVersionNo());
        templateRepository.save(template);

        auditService.record(AuditEntry.action(AuditActions.APPROVAL_TEMPLATE_VERSION_CREATED, RiskLevel.ELEVATED)
                .resource("APPROVAL_TEMPLATE", templateId)
                .after(java.util.Map.of("version", nextVersion, "nodes", request.nodes().size()))
                .reason("发布审批模板新版本（已运行实例继续使用旧版本）"));
        return toDetail(template);
    }

    // --- 内部方法 ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public ApprovalTemplateVersion latestVersion(ApprovalTemplate template) {
        return versionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId()).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("审批模板尚未发布版本，请联系管理员"));
    }

    @Transactional(readOnly = true)
    public TemplateVersionView latestVersionView(Long templateId) {
        ApprovalTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> ApiException.notFound("审批模板不存在"));
        return toVersionView(latestVersion(template));
    }

    private ApprovalTemplateVersion publish(ApprovalTemplate template, int versionNo,
            List<com.easyoa.approval.dto.FormFieldView> formFields,
            List<com.easyoa.approval.dto.NodeDefinitionView> nodes, SecurityUser actor) {
        return versionRepository.save(new ApprovalTemplateVersion(template, versionNo, template.getName(),
                template.getDescription(), schemaCodec.writeFormSchema(formFields), schemaCodec.writeNodeSchema(nodes),
                actor.id()));
    }

    private TemplateDetailView toDetail(ApprovalTemplate template) {
        ApprovalTemplateVersion version = latestVersion(template);
        return new TemplateDetailView(template.getId(), template.getName(), template.getDescription(),
                template.isEnabled(), template.getLatestVersionNo(), template.getUpdatedAt(),
                toVersionView(version));
    }

    private TemplateVersionView toVersionView(ApprovalTemplateVersion version) {
        return new TemplateVersionView(version.getVersionNo(), version.getName(), version.getDescription(),
                schemaCodec.readFormSchema(version.getFormSchema()), schemaCodec.readNodeSchema(version.getNodeSchema()),
                version.getCreatedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}