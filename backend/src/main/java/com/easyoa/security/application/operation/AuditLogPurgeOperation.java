package com.easyoa.security.application.operation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.security.application.AbstractSensitiveOperation;
import com.easyoa.security.domain.SensitiveOperationType;

/**
 * 审计日志清理：按保留期限删除历史审计日志。
 *
 * <p>这是审计「Append Only」原则唯一的受控例外：只能通过 ROOT 高危操作通道触发，
 * 且清理动作本身会写入审计与安全事件（清理记录不被本次删除影响，因为其时间晚于截止点）。
 */
@Component
public class AuditLogPurgeOperation extends AbstractSensitiveOperation {

    /** 默认保留天数。 */
    private static final int DEFAULT_RETENTION_DAYS = 180;
    private static final int MIN_RETENTION_DAYS = 7;

    private final JdbcTemplate jdbcTemplate;

    public AuditLogPurgeOperation(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SensitiveOperationType type() {
        return SensitiveOperationType.AUDIT_LOG_PURGE;
    }

    @Override
    protected List<String> impacts(Long targetId, Map<String, Object> payload) {
        int retentionDays = retentionDays(payload);
        Instant cutoff = cutoff(retentionDays);
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where created_at < ?", Long.class, Timestamp.from(cutoff));
        long total = count == null ? 0L : count;
        return List.of(
                "将永久删除 " + total + " 条审计日志（创建时间早于 " + cutoff + "，保留最近 " + retentionDays + " 天）",
                "安全事件、账号与业务数据不受影响",
                "删除不可恢复");
    }

    @Override
    @Transactional
    public Map<String, Object> execute(Long targetId, Map<String, Object> payload, Long actorId, String reason) {
        int retentionDays = retentionDays(payload);
        Instant cutoff = cutoff(retentionDays);
        int deleted = jdbcTemplate.update("delete from audit_logs where created_at < ?", Timestamp.from(cutoff));
        return Map.of(
                "deletedCount", deleted,
                "retentionDays", retentionDays,
                "cutoff", cutoff.toString());
    }

    private int retentionDays(Map<String, Object> payload) {
        int value = intPayload(payload, "retentionDays", DEFAULT_RETENTION_DAYS);
        return Math.max(MIN_RETENTION_DAYS, value);
    }

    private Instant cutoff(int retentionDays) {
        return Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    }
}
