package com.easyoa.insights.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.insights.dto.ApprovalEfficiencyView;
import com.easyoa.insights.dto.OverdueTaskView;
import com.easyoa.insights.dto.ProjectHealthStats;
import com.easyoa.insights.dto.TaskTrendPoint;
import com.easyoa.insights.dto.WorkloadView;

/**
 * 数据中心只读查询层。
 *
 * <p>说明：这是全系统唯一直接跨模块读取业务表的地方，且**只读**。
 * 理由：报表需要的是聚合结果（count / avg / group by），
 * 若通过各模块的领域服务拼装会产生大量 N+1 查询与无意义的内存聚合；
 * 因此这里使用显式 SQL 直接投影为只读模型（DTO），不涉及任何写操作与实体加载。
 *
 * <p>数据范围由调用方传入的 projectIds 决定（空集合时直接返回空结果，
 * 避免生成非法的 `in ()` 语句）。
 */
@Repository
public class InsightsRepository {

    /** 未完成状态（用于逾期与负载统计）。 */
    private static final String OPEN_STATUSES = "('TODO', 'ACTIVE', 'REVIEW')";

    private final JdbcTemplate jdbcTemplate;

    public InsightsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 可见项目（不含已归档）。all=true 时返回全部项目。 */
    @Transactional(readOnly = true)
    public List<Long> visibleProjectIds(Long userId, boolean all) {
        if (all) {
            return jdbcTemplate.queryForList(
                    "select id from projects where status <> 'ARCHIVED' order by id asc", Long.class);
        }
        return jdbcTemplate.queryForList("""
                select pm.project_id
                from project_members pm
                join projects p on p.id = pm.project_id
                where pm.user_id = ? and p.status <> 'ARCHIVED'
                order by pm.project_id asc
                """, Long.class, userId);
    }

    @Transactional(readOnly = true)
    public List<ProjectHealthStats> projectHealth(List<Long> projectIds, int limit) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(projectIds.size());
        String sql = """
                select p.id, p.name, p.status, p.progress, p.planned_end_at,
                       count(t.id) as total_tasks,
                       count(t.id) filter (where s.system_type in ('DONE', 'CLOSED')) as done_tasks,
                       count(t.id) filter (where s.system_type in %s and t.planned_end_at < now()) as overdue_tasks
                from projects p
                left join tasks t on t.project_id = p.id
                left join task_statuses s on s.id = t.status_id
                where p.id in (%s)
                group by p.id, p.name, p.status, p.progress, p.planned_end_at
                order by overdue_tasks desc, p.updated_at desc
                limit ?
                """.formatted(OPEN_STATUSES, placeholders);

        List<Object> args = new ArrayList<>(projectIds);
        args.add(limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ProjectHealthStats(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getInt("progress"),
                instant(rs, "planned_end_at"),
                rs.getInt("total_tasks"),
                rs.getInt("done_tasks"),
                rs.getInt("overdue_tasks")), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<TaskTrendPoint> taskTrend(List<Long> projectIds, int weeks) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(projectIds.size());
        String sql = """
                with buckets as (
                    select generate_series(
                        date_trunc('week', now()) - make_interval(weeks => ?::int - 1),
                        date_trunc('week', now()),
                        interval '1 week') as week_start
                )
                select to_char(b.week_start, 'YYYY-MM-DD') as week_start,
                       (select count(*) from tasks t
                         where t.project_id in (%s) and t.created_at >= b.week_start
                           and t.created_at < b.week_start + interval '1 week') as created_count,
                       (select count(*) from tasks t
                         where t.project_id in (%s) and t.completed_at >= b.week_start
                           and t.completed_at < b.week_start + interval '1 week') as completed_count
                from buckets b
                order by b.week_start asc
                """.formatted(placeholders, placeholders);

        List<Object> args = new ArrayList<>();
        args.add(weeks);
        args.addAll(projectIds);
        args.addAll(projectIds);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TaskTrendPoint(
                rs.getString("week_start"),
                rs.getInt("created_count"),
                rs.getInt("completed_count")), args.toArray());
    }

    @Transactional(readOnly = true)
    public List<OverdueTaskView> overdueTasks(List<Long> projectIds, int limit) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(projectIds.size());
        String sql = """
                select t.id, t.title, t.project_id, p.name as project_name,
                       coalesce(u.display_name, '未指派') as assignee,
                       t.planned_end_at, t.progress
                from tasks t
                join projects p on p.id = t.project_id
                join task_statuses s on s.id = t.status_id
                left join users u on u.id = t.primary_assignee_id
                where t.project_id in (%s)
                  and s.system_type in %s
                  and t.planned_end_at < now()
                order by t.planned_end_at asc
                limit ?
                """.formatted(placeholders, OPEN_STATUSES);

        List<Object> args = new ArrayList<>(projectIds);
        args.add(limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Instant plannedEnd = instant(rs, "planned_end_at");
            return new OverdueTaskView(
                    rs.getLong("id"),
                    rs.getString("title"),
                    rs.getLong("project_id"),
                    rs.getString("project_name"),
                    rs.getString("assignee"),
                    plannedEnd,
                    rs.getInt("progress"),
                    overdueDays(plannedEnd));
        }, args.toArray());
    }

    @Transactional(readOnly = true)
    public List<WorkloadView> workload(List<Long> projectIds, int limit) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(projectIds.size());
        String sql = """
                select u.id, u.display_name,
                       count(*) as open_tasks,
                       count(*) filter (where t.planned_end_at < now()) as overdue_tasks
                from tasks t
                join users u on u.id = t.primary_assignee_id
                join task_statuses s on s.id = t.status_id
                where t.project_id in (%s) and s.system_type in %s
                group by u.id, u.display_name
                order by open_tasks desc, u.display_name asc
                limit ?
                """.formatted(placeholders, OPEN_STATUSES);

        List<Object> args = new ArrayList<>(projectIds);
        args.add(limit);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new WorkloadView(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getInt("open_tasks"),
                rs.getInt("overdue_tasks")), args.toArray());
    }

    /**
     * 审批效率。
     *
     * @param allScope true 表示统计全部审批（ROOT / ADMIN），否则只统计「我发起或我参与审批」的实例
     */
    @Transactional(readOnly = true)
    public ApprovalEfficiencyView approvalEfficiency(Long userId, boolean allScope) {
        String scopeClause = allScope ? "" : """
                  and (i.applicant_id = ?
                       or exists (select 1
                                  from approval_nodes n
                                  join approval_node_approvers a on a.node_id = n.id
                                  where n.instance_id = i.id and a.user_id = ?))
                """;
        String sql = """
                select
                    count(*) filter (where i.status = 'PENDING') as pending,
                    count(*) filter (where i.status = 'APPROVED') as approved,
                    count(*) filter (where i.status = 'REJECTED') as rejected,
                    count(*) filter (where i.status = 'RETURNED') as returned,
                    count(*) filter (where i.finished_at is not null) as finished_count,
                    avg(extract(epoch from (i.finished_at - i.submitted_at)) / 3600.0)
                        filter (where i.finished_at is not null and i.submitted_at is not null) as avg_hours,
                    max(i.finished_at) as last_finished_at
                from approval_instances i
                where 1 = 1
                %s
                """.formatted(scopeClause);

        List<Object> args = new ArrayList<>();
        if (!allScope) {
            args.add(userId);
            args.add(userId);
        }
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            int approved = rs.getInt("approved");
            int rejected = rs.getInt("rejected");
            int decided = approved + rejected;
            Object avg = rs.getObject("avg_hours");
            Double averageHours = avg == null ? null : Math.round(((Number) avg).doubleValue() * 10) / 10.0;
            Double approvedRate = decided == 0 ? null : Math.round(approved * 1000.0 / decided) / 10.0;
            return new ApprovalEfficiencyView(
                    rs.getInt("pending"),
                    approved,
                    rejected,
                    rs.getInt("returned"),
                    rs.getInt("finished_count"),
                    averageHours,
                    approvedRate,
                    instant(rs, "last_finished_at"));
        }, args.toArray());
    }

    private String placeholders(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "?").collect(Collectors.joining(", "));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static long overdueDays(Instant plannedEnd) {
        if (plannedEnd == null) {
            return 0;
        }
        long hours = Duration.between(plannedEnd, Instant.now()).toHours();
        return Math.max(1, (hours + 23) / 24);
    }
}
