package com.easyoa.security.application.operation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.security.application.AbstractSensitiveOperation;
import com.easyoa.security.domain.SensitiveOperationType;

/**
 * 系统核心数据销毁：清空全部业务数据，保留账号 / 组织架构 / 系统设置 / 安全事件。
 *
 * <p>删除顺序严格遵守外键依赖（先子表后父表）。附件二进制文件不在本操作范围内，
 * 影响范围中会明确提示，避免操作者误以为磁盘数据已被清除。
 */
@Component
public class SystemDataDestructionOperation extends AbstractSensitiveOperation {

    /** 业务数据表（按外键依赖从子到父排列）。 */
    private static final List<String> PURGE_ORDER = List.of(
            "comment_mentions",
            "comment_versions",
            "comments",
            "files",
            "task_collaborators",
            "task_dependencies",
            "tasks",
            "task_statuses",
            "approval_actions",
            "approval_node_approvers",
            "approval_nodes",
            "approval_instances",
            "approval_template_versions",
            "approval_templates",
            "project_members",
            "projects",
            "notifications");

    /** 影响范围中展示的关键表（表名 → 展示名）。 */
    private static final Map<String, String> HIGHLIGHT = Map.of(
            "projects", "项目",
            "tasks", "任务",
            "approval_instances", "审批实例",
            "comments", "评论",
            "files", "附件元数据",
            "notifications", "通知");

    private final JdbcTemplate jdbcTemplate;

    public SystemDataDestructionOperation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SensitiveOperationType type() {
        return SensitiveOperationType.DATA_DESTRUCTION;
    }

    @Override
    protected List<String> impacts(Long targetId, Map<String, Object> payload) {
        return List.of(
                "将永久删除：" + highlightSummary(),
                "账号、组织架构、系统设置、审计日志与安全事件将保留",
                "附件二进制文件保留在服务器存储目录，需按部署规范人工清理",
                "操作不可撤销");
    }

    private String highlightSummary() {
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, String> entry : HIGHLIGHT.entrySet()) {
            if (summary.length() > 0) {
                summary.append(" / ");
            }
            summary.append(entry.getValue()).append(' ').append(count(entry.getKey()));
        }
        return summary.toString();
    }

    @Override
    @Transactional
    public Map<String, Object> execute(Long targetId, Map<String, Object> payload, Long actorId, String reason) {
        Map<String, Object> result = new HashMap<>();
        long total = 0L;
        for (String table : PURGE_ORDER) {
            int deleted = jdbcTemplate.update("delete from " + table);
            total += deleted;
            if (deleted > 0) {
                result.put(table, deleted);
            }
        }
        result.put("totalDeletedRows", total);
        return result;
    }

    private long count(String table) {
        Long value = jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0L : value;
    }
}
