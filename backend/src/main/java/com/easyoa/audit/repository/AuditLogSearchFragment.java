package com.easyoa.audit.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.easyoa.audit.domain.AuditLogRecord;
import com.easyoa.audit.dto.AuditLogQuery;

/**
 * 审计检索能力（自定义片段）。
 *
 * <p>刻意不使用 {@code JpaSpecificationExecutor}：该接口在较新版本中带有
 * {@code delete(Specification)}，会破坏审计日志的 Append Only 结构保证。
 */
public interface AuditLogSearchFragment {

    Page<AuditLogRecord> search(AuditLogQuery query, Pageable pageable);
}