package com.easyoa.security.application.operation;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.security.application.AbstractSensitiveOperation;
import com.easyoa.security.domain.SensitiveOperationType;

/**
 * 敏感数据导出：将审计日志导出为 CSV（含操作者、客户端 IP 与请求 ID）。
 *
 * <p>导出属于数据外带行为，因此与销毁、清理同级：必须经 ROOT 高危操作通道，
 * 并在安全事件中永久留痕。
 */
@Component
public class AuditLogExportOperation extends AbstractSensitiveOperation {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private static final String SELECT_SQL = """
            select id, created_at, actor_username, action, resource_type, resource_id,
                   risk_level, ip_address, request_id, reason
            from audit_logs
            order by id asc
            """;

    private final JdbcTemplate jdbcTemplate;

    public AuditLogExportOperation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SensitiveOperationType type() {
        return SensitiveOperationType.SENSITIVE_DATA_EXPORT;
    }

    @Override
    protected List<String> impacts(Long targetId, Map<String, Object> payload) {
        Long count = jdbcTemplate.queryForObject("select count(*) from audit_logs", Long.class);
        long total = count == null ? 0L : count;
        return List.of(
                "将导出全部 " + total + " 条审计日志为 CSV 文件",
                "导出内容包含操作者、客户端 IP、请求 ID 等溯源信息",
                "导出行为本身会写入安全事件");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> execute(Long targetId, Map<String, Object> payload, Long actorId, String reason) {
        List<String> lines = jdbcTemplate.query(SELECT_SQL, (rs, rowNum) -> String.join(",",
                csv(String.valueOf(rs.getLong("id"))),
                csv(rs.getTimestamp("created_at") == null ? "" : rs.getTimestamp("created_at").toInstant().toString()),
                csv(rs.getString("actor_username")),
                csv(rs.getString("action")),
                csv(rs.getString("resource_type")),
                csv(rs.getString("resource_id")),
                csv(rs.getString("risk_level")),
                csv(rs.getString("ip_address")),
                csv(rs.getString("request_id")),
                csv(rs.getString("reason"))));

        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("id,created_at,actor_username,action,resource_type,resource_id,risk_level,ip_address,request_id,reason\n");
        for (String line : lines) {
            csv.append(line).append('\n');
        }
        String fileName = "easyoa-audit-" + FILE_STAMP.format(Instant.now()) + ".csv";
        return Map.of(
                "fileName", fileName,
                "rowCount", lines.size(),
                "content", csv.toString());
    }

    /** CSV 字段转义：统一加引号并转义内部引号，避免导出内容破坏表格结构（CSV 注入）。 */
    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
