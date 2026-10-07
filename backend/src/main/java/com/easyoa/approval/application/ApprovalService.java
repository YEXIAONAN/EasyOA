package com.easyoa.approval.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.domain.ApprovalAction;
import com.easyoa.approval.domain.ApprovalActionType;
import com.easyoa.approval.domain.ApprovalInstance;
import com.easyoa.approval.domain.ApprovalNode;
import com.easyoa.approval.domain.ApprovalNodeApprover;
import com.easyoa.approval.domain.ApprovalStatus;
import com.easyoa.approval.domain.ApproverStatus;
import com.easyoa.approval.domain.NodeMode;
import com.easyoa.approval.dto.ApprovalCardView;
import com.easyoa.approval.dto.ApprovalDetailView;
import com.easyoa.approval.dto.FormFieldView;
import com.easyoa.approval.dto.NodeDefinitionView;
import com.easyoa.approval.dto.SubmitApprovalRequest;
import com.easyoa.approval.dto.TransferApprovalRequest;
import com.easyoa.approval.dto.UpdateApprovalFormRequest;
import com.easyoa.approval.repository.ApprovalActionRepository;
import com.easyoa.approval.repository.ApprovalInstanceRepository;
import com.easyoa.approval.repository.ApprovalNodeApproverRepository;
import com.easyoa.approval.repository.ApprovalNodeRepository;
import com.easyoa.approval.repository.ApprovalTemplateRepository;
import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.file.application.FileService;
import com.easyoa.notification.application.NotificationService;
import com.easyoa.notification.domain.NotificationType;
import com.easyoa.task.dto.TaskUserBrief;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 审批核心服务：发起 / 提交 / 同意 / 拒绝 / 退回 / 撤回 / 转交 与查询。
 *
 * <p>关键规则：
 * <ul>
 *   <li>提交时解析动态审批人并生成快照（含自我审批禁止与默认递补链），
 *       解析失败禁止提交（绝不静默跳过节点）；</li>
 *   <li>表单与流程双快照：进入 PENDING 后申请人不能修改表单；</li>
 *   <li>退回后从第一个节点重新审批（禁止从退回节点继续）；</li>
 *   <li>ANY_ONE 任意一人通过即进入下一节点；ALL 需要全部待审批人通过；</li>
 *   <li>运行中实例的审批人不受组织变化影响，只能由管理员转交（完整审计）。</li>
 * </ul>
 */
@Service
public class ApprovalService {

    private static final int WORKSPACE_LIST_LIMIT = 5;

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalNodeRepository nodeRepository;
    private final ApprovalNodeApproverRepository approverRepository;
    private final ApprovalActionRepository actionRepository;
    private final ApprovalTemplateRepository templateRepository;
    private final ApprovalTemplateService templateService;
    private final ApprovalSchemaCodec schemaCodec;
    private final ApproverResolver approverResolver;
    private final ApprovalPermissionService permissionService;
    private final FileService fileService;
    private final UserService userService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public ApprovalService(ApprovalInstanceRepository instanceRepository, ApprovalNodeRepository nodeRepository,
            ApprovalNodeApproverRepository approverRepository, ApprovalActionRepository actionRepository,
            ApprovalTemplateRepository templateRepository, ApprovalTemplateService templateService,
            ApprovalSchemaCodec schemaCodec, ApproverResolver approverResolver,
            ApprovalPermissionService permissionService, FileService fileService, UserService userService,
            AuditService auditService, NotificationService notificationService) {
        this.instanceRepository = instanceRepository;
        this.nodeRepository = nodeRepository;
        this.approverRepository = approverRepository;
        this.actionRepository = actionRepository;
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.schemaCodec = schemaCodec;
        this.approverResolver = approverResolver;
        this.permissionService = permissionService;
        this.fileService = fileService;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // --- 查询 -------------------------------------------------------------------

    public enum ApprovalScope {
        PENDING, MINE, FINISHED
    }

    @Transactional(readOnly = true)
    public PageResponse<ApprovalCardView> list(String scope, Long userId, int page, int size) {
        ApprovalScope parsed = parseScope(scope);
        PageRequest pageable = PageRequest.of(Math.max(page, 1) - 1, Math.max(size, 1));
        Page<ApprovalInstance> result = switch (parsed) {
            case PENDING -> instanceRepository.searchPendingForApprover(userId, ApprovalStatus.PENDING,
                    ApproverStatus.PENDING, pageable);
            case MINE -> instanceRepository.searchMine(userId, pageable);
            case FINISHED -> instanceRepository.searchFinishedForParticipant(userId,
                    List.of(ApprovalStatus.APPROVED, ApprovalStatus.REJECTED, ApprovalStatus.CANCELLED), pageable);
        };
        return toCards(result, page, size);
    }

    @Transactional(readOnly = true)
    public ApprovalDetailView detail(Long instanceId) {
        SecurityUser actor = permissionService.requireAuthenticated();
        ApprovalInstance instance = permissionService.requireViewable(instanceId);
        ApprovalSchemaCodec.Snapshot snapshot = schemaCodec.readSnapshot(instance.getFormSnapshot());

        List<ApprovalNode> nodes = nodeRepository.findByInstanceId(instanceId);
        List<ApprovalNodeApprover> approvers = approverRepository.findByInstanceId(instanceId);
        Map<Long, List<ApprovalNodeApprover>> approversByNode = approvers.stream()
                .collect(Collectors.groupingBy(item -> item.getNode().getId()));

        Integer currentNodeIndex = instance.getCurrentNodeIndex();
        List<ApprovalDetailView.NodeView> nodeViews = new ArrayList<>();
        boolean pendingApprover = false;
        for (ApprovalNode node : nodes) {
            boolean current = currentNodeIndex != null && node.getNodeIndex() == currentNodeIndex;
            List<ApprovalDetailView.ApproverView> approverViews = new ArrayList<>();
            for (ApprovalNodeApprover approver : approversByNode.getOrDefault(node.getId(), List.of())) {
                if (current && approver.getUser().getId().equals(actor.id()) && approver.isPending()) {
                    pendingApprover = true;
                }
                approverViews.add(new ApprovalDetailView.ApproverView(
                        TaskUserBrief.from(approver.getUser()),
                        approver.getRuleType().name(),
                        approver.getStatus().name(),
                        approver.getComment(),
                        approver.getActedAt(),
                        approver.getTransferredFromUserId() != null));
            }
            nodeViews.add(new ApprovalDetailView.NodeView(node.getNodeIndex(), node.getName(),
                    node.getMode().name(), node.getStatus().name(), current, approverViews));
        }

        List<ApprovalDetailView.ActionView> actions = actionRepository.findByInstanceId(instanceId).stream()
                .map(action -> new ApprovalDetailView.ActionView(action.getAction().name(),
                        TaskUserBrief.from(action.getActor()), action.getComment(), action.getCreatedAt()))
                .toList();

        boolean applicant = instance.getApplicant().getId().equals(actor.id());
        boolean editable = instance.getStatus().isFormEditable();
        ApprovalDetailView.Permissions permissions = new ApprovalDetailView.Permissions(
                applicant && editable,
                applicant && editable,
                applicant && instance.isPending(),
                pendingApprover,
                pendingApprover,
                pendingApprover,
                permissionService.isAdminLike(actor) && instance.isPending());

        return new ApprovalDetailView(
                instance.getId(),
                instance.getTitle(),
                instance.getTemplate().getName(),
                instance.getTemplateVersionNo(),
                instance.getStatus().name(),
                TaskUserBrief.from(instance.getApplicant()),
                snapshot.schema(),
                snapshot.values(),
                fileService.listByApproval(instanceId),
                nodeViews,
                actions,
                permissions,
                instance.getCreatedAt(),
                instance.getSubmittedAt(),
                instance.getFinishedAt(),
                instance.getUpdatedAt());
    }

    /** 工作台 KPI：待我审批数量。 */
    @Transactional(readOnly = true)
    public long countPendingForApprover(Long userId) {
        return instanceRepository.countPendingForApprover(userId, ApprovalStatus.PENDING, ApproverStatus.PENDING);
    }

    /** 工作台「待我审批」区块：近期待办审批。 */
    @Transactional(readOnly = true)
    public List<ApprovalCardView> recentPendingForApprover(Long userId) {
        Page<ApprovalInstance> result = instanceRepository.searchPendingForApprover(userId, ApprovalStatus.PENDING,
                ApproverStatus.PENDING, PageRequest.of(0, WORKSPACE_LIST_LIMIT));
        return toCards(result, 1, WORKSPACE_LIST_LIMIT).items();
    }

    // --- 发起与表单 ---------------------------------------------------------------

    /** 发起审批（创建草稿；表单按模板 schema 校验）。 */
    @Transactional
    public ApprovalDetailView create(SubmitApprovalRequest request) {
        SecurityUser actor = permissionService.requireAuthenticated();
        var template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> ApiException.notFound("审批模板不存在或无权访问"));
        if (!template.isEnabled() && !permissionService.isAdminLike(actor)) {
            throw ApiException.notFound("审批模板不存在或无权访问");
        }
        var version = templateService.latestVersion(template);
        List<FormFieldView> fields = schemaCodec.readFormSchema(version.getFormSchema());
        Map<String, Object> values = request.values();
        schemaCodec.validateValues(fields, values);
        validateUserFields(fields, values);

        ApprovalInstance instance = new ApprovalInstance(template, version, userService.getById(actor.id()),
                request.title().trim(), schemaCodec.writeSnapshot(fields, values));
        instanceRepository.save(instance);
        return detail(instance.getId());
    }

    /** 修改表单（仅 DRAFT / RETURNED，由申请人本人操作）。 */
    @Transactional
    public ApprovalDetailView updateForm(Long instanceId, UpdateApprovalFormRequest request) {
        ApprovalInstance instance = permissionService.requireApplicant(instanceId);
        if (!instance.getStatus().isFormEditable()) {
            throw ApiException.conflict("审批已进入流程，表单不可修改（可撤回或等待退回）");
        }
        ApprovalSchemaCodec.Snapshot snapshot = schemaCodec.readSnapshot(instance.getFormSnapshot());
        Map<String, Object> values = request.values();
        schemaCodec.validateValues(snapshot.schema(), values);
        validateUserFields(snapshot.schema(), values);

        instance.updateFormSnapshot(schemaCodec.writeSnapshot(snapshot.schema(), values));
        instanceRepository.save(instance);
        recordAction(instance, null, ApprovalActionType.UPDATE_FORM, null);
        return detail(instanceId);
    }

    /** 提交审批：解析动态审批人并生成快照；解析不出合法审批人则禁止提交。 */
    @Transactional
    public ApprovalDetailView submit(Long instanceId) {
        SecurityUser actor = permissionService.requireAuthenticated();
        ApprovalInstance instance = permissionService.requireApplicant(instanceId);
        if (!instance.getStatus().isFormEditable()) {
            throw ApiException.conflict("当前状态不允许提交（仅草稿或已退回的申请可以提交）");
        }
        ApprovalSchemaCodec.Snapshot snapshot = schemaCodec.readSnapshot(instance.getFormSnapshot());
        List<NodeDefinitionView> definitions = schemaCodec
                .readNodeSchema(instance.getTemplateVersion().getNodeSchema());

        List<ApprovalNode> existingNodes = nodeRepository.findByInstanceId(instanceId);
        int nodeCount;
        if (existingNodes.isEmpty()) {
            // 首次提交：解析实际审批人并生成快照（组织变化后不再自动修改）
            int index = 0;
            for (NodeDefinitionView definition : definitions) {
                List<ApproverResolver.ResolvedApprover> resolved = approverResolver
                        .resolveNode(definition.approvers(), actor.id(), snapshot.values());
                ApprovalNode node = nodeRepository
                        .save(new ApprovalNode(instance, index, definition.name(), definition.mode()));
                for (ApproverResolver.ResolvedApprover approver : resolved) {
                    approverRepository.save(new ApprovalNodeApprover(node, userService.getById(approver.userId()),
                            approver.ruleType()));
                }
                index++;
            }
            nodeCount = definitions.size();
        } else {
            // 退回后重新提交：从第一个节点重新审批（审批人快照保持不变）
            for (ApprovalNode node : existingNodes) {
                node.reset();
                nodeRepository.save(node);
            }
            for (ApprovalNodeApprover approver : approverRepository.findByInstanceId(instanceId)) {
                approver.reset();
                approverRepository.save(approver);
            }
            nodeCount = existingNodes.size();
        }

        // 表单中的附件挂载到实例（仅本人上传的草稿附件）
        fileService.linkToApproval(instanceId, collectAttachmentIds(snapshot.schema(), snapshot.values()), actor);

        instance.start(0, Instant.now());
        instanceRepository.save(instance);

        recordAction(instance, null, ApprovalActionType.SUBMIT, null);
        auditService.record(AuditEntry.action(AuditActions.APPROVAL_SUBMITTED, RiskLevel.NORMAL)
                .resource("APPROVAL", instanceId)
                .after(Map.of("template", instance.getTemplate().getName(),
                        "version", instance.getTemplateVersionNo(), "nodes", nodeCount))
                .reason("提交审批"));
        notifyNodeApprovers(instanceId, 0, instance, actor.id());
        return detail(instanceId);
    }

    // --- 审批动作 ---------------------------------------------------------------

    /** 同意：ANY_ONE 直接进入下一节点；ALL 需全部待审批人通过。 */
    @Transactional
    public ApprovalDetailView approve(Long instanceId, String comment) {
        SecurityUser actor = permissionService.requireAuthenticated();
        ApprovalPermissionService.PendingApprover context = permissionService.requirePendingApprover(instanceId);
        Instant now = Instant.now();
        context.approver().approve(normalize(comment), now);
        approverRepository.save(context.approver());

        ApprovalNode node = context.node();
        boolean nodeCompleted = node.getMode() == NodeMode.ANY_ONE;
        if (!nodeCompleted) {
            List<ApprovalNodeApprover> rows = approverRepository.findByNodeIds(List.of(node.getId()));
            nodeCompleted = rows.stream()
                    .filter(row -> row.getStatus() != ApproverStatus.TRANSFERRED_OUT)
                    .allMatch(row -> row.getStatus() == ApproverStatus.APPROVED);
        }

        ApprovalInstance instance = context.instance();
        if (nodeCompleted) {
            node.approve(now);
            nodeRepository.save(node);
            int nextIndex = node.getNodeIndex() + 1;
            boolean hasNext = nodeRepository.findByInstanceId(instanceId).stream()
                    .anyMatch(item -> item.getNodeIndex() == nextIndex);
            if (hasNext) {
                instance.advanceTo(nextIndex);
            } else {
                instance.approve(now);
                auditService.record(AuditEntry.action(AuditActions.APPROVAL_APPROVED, RiskLevel.ELEVATED)
                        .resource("APPROVAL", instanceId)
                        .after(Map.of("title", instance.getTitle(), "nodes", nextIndex))
                        .reason("审批通过（全部节点完成）"));
            }
            instanceRepository.save(instance);
            if (hasNext) {
                notifyNodeApprovers(instanceId, nextIndex, instance, actor.id());
            } else {
                notificationService.notify(instance.getApplicant().getId(), NotificationType.APPROVAL_APPROVED,
                        "审批已通过", "「" + instance.getTitle() + "」已通过全部审批节点",
                        "/approvals/" + instanceId, "APPROVAL", instanceId, actor.id());
            }
        }

        recordAction(instance, node, ApprovalActionType.APPROVE, normalize(comment));
        return detail(instanceId);
    }

    /** 拒绝（必须填写原因）：实例直接终止。 */
    @Transactional
    public ApprovalDetailView reject(Long instanceId, String comment) {
        String reason = requireComment(comment, "请填写拒绝原因");
        ApprovalPermissionService.PendingApprover context = permissionService.requirePendingApprover(instanceId);
        Instant now = Instant.now();
        context.approver().reject(reason, now);
        approverRepository.save(context.approver());
        context.node().reject(now);
        nodeRepository.save(context.node());

        ApprovalInstance instance = context.instance();
        instance.reject(now);
        instanceRepository.save(instance);

        recordAction(instance, context.node(), ApprovalActionType.REJECT, reason);
        auditService.record(AuditEntry.action(AuditActions.APPROVAL_REJECTED, RiskLevel.ELEVATED)
                .resource("APPROVAL", instanceId)
                .after(Map.of("title", instance.getTitle(), "comment", reason))
                .reason("审批拒绝"));
        notificationService.notify(instance.getApplicant().getId(), NotificationType.APPROVAL_REJECTED,
                "审批被拒绝", "「" + instance.getTitle() + "」：" + reason,
                "/approvals/" + instanceId, "APPROVAL", instanceId, context.approver().getUser().getId());
        return detail(instanceId);
    }

    /** 退回（必须填写原因）：申请人修改后从第一个节点重新审批。 */
    @Transactional
    public ApprovalDetailView returnForRevision(Long instanceId, String comment) {
        String reason = requireComment(comment, "请填写退回原因");
        ApprovalPermissionService.PendingApprover context = permissionService.requirePendingApprover(instanceId);
        Instant now = Instant.now();
        context.approver().markReturned(reason, now);
        approverRepository.save(context.approver());

        ApprovalInstance instance = context.instance();
        instance.returnToApplicant();
        instanceRepository.save(instance);

        recordAction(instance, context.node(), ApprovalActionType.RETURN, reason);
        auditService.record(AuditEntry.action(AuditActions.APPROVAL_RETURNED, RiskLevel.ELEVATED)
                .resource("APPROVAL", instanceId)
                .after(Map.of("title", instance.getTitle(), "comment", reason))
                .reason("审批退回（重新提交后从第一个节点重新审批）"));
        notificationService.notify(instance.getApplicant().getId(), NotificationType.APPROVAL_RETURNED,
                "审批被退回，请修改后重新提交", "「" + instance.getTitle() + "」：" + reason,
                "/approvals/" + instanceId, "APPROVAL", instanceId, context.approver().getUser().getId());
        return detail(instanceId);
    }

    /** 撤回（仅申请人，审批中）：CANCELLED 终态。 */
    @Transactional
    public ApprovalDetailView withdraw(Long instanceId) {
        ApprovalInstance instance = permissionService.requireApplicant(instanceId);
        if (!instance.isPending()) {
            throw ApiException.conflict("只有审批中的申请可以撤回");
        }
        instance.cancel(Instant.now());
        instanceRepository.save(instance);

        recordAction(instance, null, ApprovalActionType.WITHDRAW, null);
        auditService.record(AuditEntry.action(AuditActions.APPROVAL_CANCELLED, RiskLevel.ELEVATED)
                .resource("APPROVAL", instanceId)
                .after(Map.of("title", instance.getTitle()))
                .reason("申请人撤回"));
        return detail(instanceId);
    }

    /** 转交（仅系统管理员）：原审批人退出，目标成员接替（完整审计）。 */
    @Transactional
    public ApprovalDetailView transfer(Long instanceId, TransferApprovalRequest request) {
        SecurityUser actor = permissionService.requireAdminLike();
        ApprovalInstance instance = permissionService.requireViewable(instanceId);
        if (!instance.isPending() || instance.getCurrentNodeIndex() == null) {
            throw ApiException.conflict("只有审批中的申请可以转交");
        }
        ApprovalNode node = nodeRepository.findByInstanceId(instanceId).stream()
                .filter(item -> item.getNodeIndex() == instance.getCurrentNodeIndex())
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("审批节点状态异常，请刷新后重试"));

        ApprovalNodeApprover from = approverRepository.findByNodeIdAndUserId(node.getId(), request.fromUserId())
                .filter(item -> item.getStatus() == ApproverStatus.PENDING)
                .orElseThrow(() -> ApiException.conflict("原审批人不在待审批状态，无法转交"));

        User target = userService.getById(request.toUserId());
        if (!target.isActive()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "目标成员账号已被禁用，无法接收审批");
        }
        if (target.getId().equals(instance.getApplicant().getId())) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "不能转交给申请人本人（自我审批禁止）");
        }
        if (approverRepository.findByNodeIdAndUserId(node.getId(), target.getId()).isPresent()) {
            throw ApiException.conflict("该成员已在当前节点审批人中");
        }

        from.markTransferredOut();
        approverRepository.save(from);
        ApprovalNodeApprover replacement = new ApprovalNodeApprover(node, target, from.getRuleType());
        replacement.setTransferredFromUserId(from.getUser().getId());
        approverRepository.save(replacement);

        recordAction(instance, node, ApprovalActionType.TRANSFER,
                normalize(request.comment()) == null ? "管理员转交" : normalize(request.comment()));
        auditService.record(AuditEntry.action(AuditActions.APPROVAL_TRANSFERRED, RiskLevel.CRITICAL)
                .resource("APPROVAL", instanceId)
                .before(Map.of("fromUserId", from.getUser().getId()))
                .after(Map.of("toUserId", target.getId(), "operatorId", actor.id()))
                .reason(normalize(request.comment()) == null ? "管理员转交审批" : normalize(request.comment())));
        notificationService.notify(target.getId(), NotificationType.APPROVAL_PENDING,
                "有新的审批待你处理（转交）", "「" + instance.getTitle() + "」已转交给你，当前节点："
                        + node.getName(),
                "/approvals/" + instanceId, "APPROVAL", instanceId, actor.id());
        return detail(instanceId);
    }

    // --- 内部方法 ---------------------------------------------------------------

    /** 通知某节点的待审批人（收件人自动跳过触发者）。 */
    private void notifyNodeApprovers(Long instanceId, int nodeIndex, ApprovalInstance instance, Long actorId) {
        nodeRepository.findByInstanceId(instanceId).stream()
                .filter(node -> node.getNodeIndex() == nodeIndex)
                .findFirst()
                .ifPresent(node -> {
                    List<Long> recipients = approverRepository.findByNodeIds(List.of(node.getId())).stream()
                            .filter(row -> row.getStatus() == ApproverStatus.PENDING)
                            .map(row -> row.getUser().getId())
                            .toList();
                    notificationService.notifyAll(recipients, NotificationType.APPROVAL_PENDING,
                            "有新的审批待你处理",
                            instance.getTitle() + "（当前节点：" + node.getName() + "）",
                            "/approvals/" + instanceId, "APPROVAL", instanceId, actorId);
                });
    }

    private ApprovalScope parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return ApprovalScope.PENDING;
        }
        try {
            return ApprovalScope.valueOf(scope.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ApprovalScope.PENDING;
        }
    }

    private PageResponse<ApprovalCardView> toCards(Page<ApprovalInstance> result, int page, int size) {
        if (result.isEmpty()) {
            return PageResponse.of(List.of(), page, size, 0);
        }
        Map<Long, String> currentNodeNames = currentNodeNames(result.getContent());
        List<ApprovalCardView> cards = result.getContent().stream()
                .map(instance -> new ApprovalCardView(
                        instance.getId(),
                        instance.getTitle(),
                        instance.getTemplate().getName(),
                        instance.getStatus().name(),
                        TaskUserBrief.from(instance.getApplicant()),
                        currentNodeNames.get(instance.getId()),
                        instance.getCreatedAt(),
                        instance.getSubmittedAt(),
                        instance.getUpdatedAt()))
                .toList();
        return PageResponse.of(cards, page, size, result.getTotalElements());
    }

    private Map<Long, String> currentNodeNames(List<ApprovalInstance> instances) {
        Set<Long> ids = instances.stream()
                .filter(instance -> instance.getCurrentNodeIndex() != null)
                .map(ApprovalInstance::getId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (ApprovalNode node : nodeRepository.findByInstanceIds(ids)) {
            for (ApprovalInstance instance : instances) {
                if (instance.getId().equals(node.getInstance().getId())
                        && instance.getCurrentNodeIndex() != null
                        && node.getNodeIndex() == instance.getCurrentNodeIndex()) {
                    names.put(instance.getId(), node.getName());
                }
            }
        }
        return names;
    }

    private void recordAction(ApprovalInstance instance, ApprovalNode node, ApprovalActionType actionType,
            String comment) {
        SecurityUser actor = permissionService.requireAuthenticated();
        actionRepository.save(new ApprovalAction(instance, node, userService.getById(actor.id()), actionType, comment));
    }

    private void validateUserFields(List<FormFieldView> fields, Map<String, Object> values) {
        for (FormFieldView field : fields) {
            if (!"USER".equals(field.type())) {
                continue;
            }
            Object value = values.get(field.key());
            if (!(value instanceof Number number)) {
                continue;
            }
            User user = userService.getById(number.longValue());
            if (!user.isActive()) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "「" + field.label() + "」指向的账号已被禁用");
            }
        }
    }

    private List<Long> collectAttachmentIds(List<FormFieldView> fields, Map<String, Object> values) {
        List<Long> ids = new ArrayList<>();
        for (FormFieldView field : fields) {
            if (!"ATTACHMENT".equals(field.type())) {
                continue;
            }
            Object value = values.get(field.key());
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Number number) {
                        ids.add(number.longValue());
                    }
                }
            }
        }
        return ids;
    }

    private String requireComment(String comment, String message) {
        String normalized = normalize(comment);
        if (normalized == null) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, message);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}