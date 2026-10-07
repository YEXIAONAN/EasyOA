package com.easyoa.audit.repository;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.easyoa.audit.domain.AuditLogRecord;

/**
 * 审计日志仓储。
 *
 * <p>刻意只继承基础 {@link Repository} 并只声明 {@code save / findById / count / search}，
 * 从类型层面杜绝任何 delete / update 能力（包括 Spring Data 新版本通过
 * {@code JpaSpecificationExecutor} 暴露的 delete），保证 Append Only 原则不可被绕过。
 */
public interface AuditLogRepository extends Repository<AuditLogRecord, Long>, AuditLogSearchFragment {

    AuditLogRecord save(AuditLogRecord record);

    Optional<AuditLogRecord> findById(Long id);

    long count();
}