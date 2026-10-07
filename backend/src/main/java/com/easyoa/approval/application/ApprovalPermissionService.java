package com.easyoa.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.domain.ApprovalInstance;
import com.easyoa.approval.domain.ApprovalNode;
import com.easyoa.approval.domain.ApprovalNodeApprover;
import com.easyoa.approval.domain.ApproverStatus;
import com.easyoa.approval.repository.ApprovalInstanceRepository;
import com.easyoa.approval.repository.ApprovalNodeApproverRepository;
import com.easyoa.approval.repository.ApprovalNodeRepository;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;

/**
 * 审批权限：审批域的唯一授权入口。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li><b>查看</b>：申请人、参与过审批的人、或系统管理员（审计需要）；其余一律 404
 *       （不泄露申请是否存在）；</li>
 *   <li><b>提交 / 修改表单 / 撤回</b>：仅申请人；</li>
 *   <li><b>同意 / 拒绝 / 退回</b>：仅当前节点的待审批人（提交时生成的审批人快照）；</li>
 *   <li><b>转交</b>：仅系统管理员（ADMIN / ROOT），完整审计；</li>
 *   <li><b>模板管理</b>（创建 / 编辑 / 发布版本）：仅系统管理员。</li>
 * </ul>
 */
@Service
public class ApprovalPermissionService {

    private final ApprovalInstanceRepository instanceRepository;
    private final ApprovalNodeRepository nodeRepository;
    private final ApprovalNodeApproverRepository approverRepository;

    public ApprovalPermissionService(ApprovalInstanceRepository instanceRepository,
            ApprovalNodeRepository nodeRepository, ApprovalNodeApproverRepository approverRepository) {
        this.instanceRepository = instanceRepository;
        this.nodeRepository = nodeRepository;
        this.approverRepository = approverRepository;
    }

    /** 审批实例查看（非参与人 404）。 */
    @Transactional(readOnly = true)
    public ApprovalInstance requireViewable(Long instanceId) {
        SecurityUser actor = requireAuthenticated();
        ApprovalInstance instance = findInstance(instanceId);
        if (instance.getApplicant().getId().equals(actor.id()) || actor.systemRole().isAdminLike()) {
            return instance;
        }
        if (approverRepository.existsByInstanceAndUser(instanceId, actor.id())) {
            return instance;
        }
        throw ApiException.notFound("审批不存在或无权访问");
    }

    /** 仅申请人。 */
    @Transactional(readOnly = true)
    public ApprovalInstance requireApplicant(Long instanceId) {
        ApprovalInstance instance = requireViewable(instanceId);
        SecurityUser actor = requireAuthenticated();
        if (!instance.getApplicant().getId().equals(actor.id())) {
            throw ApiException.forbidden("只有申请人可以执行该操作");
        }
        return instance;
    }

    /** 当前节点待审批人（同意 / 拒绝 / 退回）。 */
    @Transactional(readOnly = true)
    public PendingApprover requirePendingApprover(Long instanceId) {
        SecurityUser actor = requireAuthenticated();
        ApprovalInstance instance = requireViewable(instanceId);
        if (!instance.isPending() || instance.getCurrentNodeIndex() == null) {
            throw ApiException.conflict("该申请不在审批中");
        }
        ApprovalNode node = nodeRepository.findByInstanceId(instanceId).stream()
                .filter(item -> item.getNodeIndex() == instance.getCurrentNodeIndex())
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("审批节点状态异常，请刷新后重试"));
        ApprovalNodeApprover approver = approverRepository.findByNodeIdAndUserId(node.getId(), actor.id())
                .filter(item -> item.getStatus() == ApproverStatus.PENDING)
                .orElseThrow(() -> ApiException.forbidden("当前节点不需要你审批"));
        return new PendingApprover(instance, node, approver);
    }

    /** 管理员（模板管理 / 转交）。 */
    public SecurityUser requireAdminLike() {
        SecurityUser actor = requireAuthenticated();
        if (!actor.systemRole().isAdminLike()) {
            throw ApiException.forbidden("只有系统管理员可以执行该操作");
        }
        return actor;
    }

    public boolean isAdminLike(SecurityUser actor) {
        return actor != null && actor.systemRole().isAdminLike();
    }

    public SecurityUser requireAuthenticated() {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }

    private ApprovalInstance findInstance(Long instanceId) {
        return instanceRepository.findByIdWithDetails(instanceId)
                .orElseThrow(() -> ApiException.notFound("审批不存在或无权访问"));
    }

    /** 当前节点待审批上下文。 */
    public record PendingApprover(ApprovalInstance instance, ApprovalNode node, ApprovalNodeApprover approver) {
    }
}