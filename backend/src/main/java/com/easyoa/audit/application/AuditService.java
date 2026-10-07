package com.easyoa.audit.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.domain.AuditLogRecord;
import com.easyoa.audit.dto.AuditLogQuery;
import com.easyoa.audit.dto.AuditLogView;
import com.easyoa.audit.repository.AuditLogRepository;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 审计服务（Append Only）。
 *
 * <p>应用层只提供 {@code record()} 与 {@code query()}：
 * 不存在任何 update / delete 能力，仓储层同样不可表达删除。
 *
 * <p>事务语义：默认加入当前事务（与业务变更同事务提交）；
 * 登录失败等无业务事务的场景会自行开启事务。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(AuditEntry entry) {
        SecurityUser current = RequestContext.currentUser();
        Long actorUserId = entry.actorUserId() != null ? entry.actorUserId() : (current == null ? null : current.id());
        String actorUsername = entry.actorUsername() != null ? entry.actorUsername()
                : (current == null ? "system" : current.username());

        AuditLogRecord record = new AuditLogRecord(
                actorUserId,
                actorUsername,
                entry.action(),
                entry.resourceType(),
                entry.resourceId(),
                toJson(entry.beforeData(), entry.action(), "beforeData"),
                toJson(entry.afterData(), entry.action(), "afterData"),
                entry.reason(),
                RequestContext.clientIp(),
                RequestContext.userAgent(),
                RequestContext.requestId(),
                entry.riskLevel());
        auditLogRepository.save(record);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> query(AuditLogQuery query) {
        int page = Math.max(query.page(), 1);
        int size = Math.min(Math.max(query.size(), 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));

        Page<AuditLogRecord> result = auditLogRepository.search(query, pageable);
        return PageResponse.from(result, AuditLogView::from);
    }

    private String toJson(Object data, String action, String field) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            // 审计不能因为附加数据无法序列化而丢失：保留记录本身，附加数据置空并告警。
            log.warn("审计附加数据序列化失败 action={} field={} type={}", action, field,
                    data.getClass().getName(), ex);
            return null;
        }
    }
}