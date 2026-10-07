package com.easyoa.approval.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.domain.ApproverRuleType;
import com.easyoa.approval.dto.ApproverRuleView;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.organization.domain.OrgMembership;
import com.easyoa.organization.domain.OrgUnit;
import com.easyoa.organization.repository.OrgMembershipRepository;
import com.easyoa.organization.repository.OrgUnitRepository;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.domain.UserStatus;
import com.easyoa.user.repository.UserRepository;

/**
 * 动态审批人解析：提交审批时把规则解析为实际审批人，随后由调用方生成审批人快照。
 *
 * <p>解析规则：
 * <ul>
 *   <li>FIXED_USER / SYSTEM_ROLE / PROJECT_OWNER / PROJECT_DEPUTY 直接解析；</li>
 *   <li>DIRECT_MANAGER（等价 ORG_UNIT_MANAGER）：从主部门沿组织链向上找最近的负责人，
 *       跳过申请人本人；PRIMARY_DEPT_MANAGER 只看主部门一层；</li>
 *   <li><b>自我审批禁止</b>：任何解析结果都会过滤申请人本人；</li>
 *   <li>规则解析为空时使用规则自带 fallback，其次系统默认递补链
 *       （主部门负责人 → SYSTEM_ROLE:ADMIN → SYSTEM_ROLE:ROOT）；</li>
 *   <li>仍无法解析出合法审批人 → 禁止提交（「审批流程配置不完整，请联系管理员」），
 *       绝不静默跳过节点。</li>
 * </ul>
 */
@Service
public class ApproverResolver {

    private static final int MAX_FALLBACK_DEPTH = 3;

    private final OrgMembershipRepository orgMembershipRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    public ApproverResolver(OrgMembershipRepository orgMembershipRepository, OrgUnitRepository orgUnitRepository,
            ProjectMemberRepository projectMemberRepository, UserRepository userRepository) {
        this.orgMembershipRepository = orgMembershipRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = userRepository;
    }

    /** 解析结果（含来源规则，写入审批人快照供审计）。 */
    public record ResolvedApprover(Long userId, ApproverRuleType ruleType) {
    }

    /**
     * 解析一个节点：逐条规则解析并合并去重；全部解析失败则抛错（禁止提交）。
     */
    @Transactional(readOnly = true)
    public List<ResolvedApprover> resolveNode(List<ApproverRuleView> rules, Long applicantId,
            Map<String, Object> formValues) {
        List<ResolvedApprover> resolved = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ApproverRuleView rule : rules) {
            for (ResolvedApprover approver : resolveWithFallback(rule, applicantId, formValues, 0)) {
                if (!applicantId.equals(approver.userId()) && seen.add(approver.userId())) {
                    resolved.add(approver);
                }
            }
        }
        if (resolved.isEmpty()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "审批流程配置不完整，请联系管理员");
        }
        return resolved;
    }

    private List<ResolvedApprover> resolveWithFallback(ApproverRuleView rule, Long applicantId,
            Map<String, Object> formValues, int depth) {
        List<ResolvedApprover> direct = filterValid(resolveDirect(rule, applicantId, formValues), applicantId);
        if (!direct.isEmpty()) {
            return direct;
        }
        if (depth >= MAX_FALLBACK_DEPTH) {
            return List.of();
        }
        List<ApproverRuleView> chain = rule.fallback() == null || rule.fallback().isEmpty()
                ? defaultFallback(rule)
                : rule.fallback();
        for (ApproverRuleView fallbackRule : chain) {
            List<ResolvedApprover> resolved = resolveWithFallback(fallbackRule, applicantId, formValues, depth + 1);
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        return List.of();
    }

    /** 系统默认递补链。 */
    private List<ApproverRuleView> defaultFallback(ApproverRuleView rule) {
        return switch (rule.type()) {
            case SYSTEM_ROLE -> List.of(new ApproverRuleView(ApproverRuleType.SYSTEM_ROLE, null, "ROOT", null, null));
            default -> List.of(
                    new ApproverRuleView(ApproverRuleType.SYSTEM_ROLE, null, "ADMIN", null, null),
                    new ApproverRuleView(ApproverRuleType.SYSTEM_ROLE, null, "ROOT", null, null));
        };
    }

    private List<ResolvedApprover> resolveDirect(ApproverRuleView rule, Long applicantId,
            Map<String, Object> formValues) {
        return switch (rule.type()) {
            case FIXED_USER -> rule.userId() == null
                    ? List.of()
                    : List.of(new ResolvedApprover(rule.userId(), ApproverRuleType.FIXED_USER));
            case PRIMARY_DEPT_MANAGER -> {
                Long unitId = primaryUnitId(applicantId);
                if (unitId == null) {
                    yield List.of();
                }
                yield orgUnitRepository.findById(unitId)
                        .map(unit -> unit.getManagerUserId() == null
                                ? List.<ResolvedApprover>of()
                                : List.of(new ResolvedApprover(unit.getManagerUserId(),
                                        ApproverRuleType.PRIMARY_DEPT_MANAGER)))
                        .orElse(List.of());
            }
            case DIRECT_MANAGER, ORG_UNIT_MANAGER -> {
                List<ResolvedApprover> candidates = new ArrayList<>();
                Long currentId = primaryUnitId(applicantId);
                while (currentId != null) {
                    OrgUnit unit = orgUnitRepository.findById(currentId).orElse(null);
                    if (unit == null) {
                        break;
                    }
                    if (unit.getManagerUserId() != null && !unit.getManagerUserId().equals(applicantId)) {
                        candidates.add(new ResolvedApprover(unit.getManagerUserId(), rule.type()));
                        break;
                    }
                    currentId = unit.getParentId();
                }
                yield candidates;
            }
            case PROJECT_OWNER -> projectRole(rule, formValues, ProjectRole.OWNER);
            case PROJECT_DEPUTY -> projectRole(rule, formValues, ProjectRole.DEPUTY_OWNER);
            case SYSTEM_ROLE -> {
                SystemRole role;
                try {
                    role = SystemRole.valueOf(rule.systemRole());
                } catch (IllegalArgumentException ex) {
                    yield List.of();
                }
                yield userRepository.findActiveBySystemRole(role, UserStatus.ACTIVE).stream()
                        .map(user -> new ResolvedApprover(user.getId(), ApproverRuleType.SYSTEM_ROLE))
                        .toList();
            }
        };
    }

    private List<ResolvedApprover> projectRole(ApproverRuleView rule, Map<String, Object> formValues,
            ProjectRole role) {
        if (rule.projectField() == null || formValues == null) {
            return List.of();
        }
        Object raw = formValues.get(rule.projectField());
        if (!(raw instanceof Number number)) {
            return List.of();
        }
        return projectMemberRepository.findFirstByProjectIdAndRole(number.longValue(), role)
                .map(member -> List.of(new ResolvedApprover(member.getUser().getId(), rule.type())))
                .orElse(List.of());
    }

    /** 过滤申请人本人与已禁用账号。 */
    private List<ResolvedApprover> filterValid(List<ResolvedApprover> candidates, Long applicantId) {
        List<ResolvedApprover> valid = new ArrayList<>();
        for (ResolvedApprover candidate : candidates) {
            if (candidate.userId() == null || candidate.userId().equals(applicantId)) {
                continue;
            }
            User user = userRepository.findById(candidate.userId()).orElse(null);
            if (user != null && user.getStatus() == UserStatus.ACTIVE) {
                valid.add(candidate);
            }
        }
        return valid;
    }

    private Long primaryUnitId(Long userId) {
        return orgMembershipRepository.findFirstByUserIdAndPrimaryTrue(userId)
                .map(OrgMembership::getOrgUnit)
                .map(OrgUnit::getId)
                .orElse(null);
    }
}