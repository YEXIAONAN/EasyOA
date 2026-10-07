package com.easyoa.insights.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.insights.dto.ApprovalEfficiencyView;
import com.easyoa.insights.dto.InsightsResponse;
import com.easyoa.insights.dto.OverdueTaskView;
import com.easyoa.insights.dto.ProjectHealthStats;
import com.easyoa.insights.dto.ProjectHealthView;
import com.easyoa.insights.dto.TaskTrendPoint;
import com.easyoa.insights.dto.WorkloadView;
import com.easyoa.insights.repository.InsightsRepository;
import com.easyoa.user.domain.SystemRole;

/**
 * 数据中心（Phase 9）。
 *
 * <p>数据范围：ROOT / ADMIN 看到全局，其余成员只看到自己参与的项目与自己相关的审批。
 * 与其它模块一致——数据可见性由后端判定，不看前端筛选。
 *
 * <p>保持克制：不引入图表库，只输出 5 组有决策价值的数据。
 */
@Service
public class InsightsService {

    private static final int HEALTH_LIMIT = 8;
    private static final int OVERDUE_LIMIT = 8;
    private static final int WORKLOAD_LIMIT = 8;
    private static final int TREND_WEEKS = 6;

    private final InsightsRepository insightsRepository;

    public InsightsService(InsightsRepository insightsRepository) {
        this.insightsRepository = insightsRepository;
    }

    @Transactional(readOnly = true)
    public InsightsResponse overview() {
        SecurityUser user = RequestContext.currentUser();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        boolean globalScope = user.systemRole() == SystemRole.ROOT || user.systemRole() == SystemRole.ADMIN;
        List<Long> projectIds = insightsRepository.visibleProjectIds(user.id(), globalScope);

        List<ProjectHealthView> health = insightsRepository.projectHealth(projectIds, HEALTH_LIMIT).stream()
                .map(InsightsService::toHealthView)
                .toList();
        List<TaskTrendPoint> trend = insightsRepository.taskTrend(projectIds, TREND_WEEKS);
        List<OverdueTaskView> overdue = insightsRepository.overdueTasks(projectIds, OVERDUE_LIMIT);
        List<WorkloadView> workload = insightsRepository.workload(projectIds, WORKLOAD_LIMIT);
        ApprovalEfficiencyView approvals = insightsRepository.approvalEfficiency(user.id(), globalScope);

        return new InsightsResponse(
                globalScope ? "ALL" : "MY_PROJECTS",
                Instant.now(),
                health,
                trend,
                overdue,
                workload,
                approvals);
    }

    /**
     * 健康度判定（规则保持简单且可解释）：
     * <ul>
     *   <li>计划结束时间已过但未完成 → BLOCKED；</li>
     *   <li>存在逾期任务 → AT_RISK；</li>
     *   <li>其余 → HEALTHY。</li>
     * </ul>
     */
    private static ProjectHealthView toHealthView(ProjectHealthStats stats) {
        String health;
        boolean endPassed = stats.plannedEndAt() != null && stats.plannedEndAt().isBefore(Instant.now());
        if (endPassed && stats.progress() < 100) {
            health = "BLOCKED";
        } else if (stats.overdueTasks() > 0) {
            health = "AT_RISK";
        } else {
            health = "HEALTHY";
        }
        return new ProjectHealthView(
                stats.projectId(),
                stats.name(),
                stats.status(),
                stats.progress(),
                stats.plannedEndAt(),
                stats.totalTasks(),
                stats.doneTasks(),
                stats.overdueTasks(),
                health);
    }
}
