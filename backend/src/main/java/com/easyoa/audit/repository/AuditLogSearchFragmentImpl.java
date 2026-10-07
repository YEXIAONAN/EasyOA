package com.easyoa.audit.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.easyoa.audit.domain.AuditLogRecord;
import com.easyoa.audit.dto.AuditLogQuery;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * {@link AuditLogSearchFragment} 的 Criteria API 实现（只读）。
 */
public class AuditLogSearchFragmentImpl implements AuditLogSearchFragment {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<AuditLogRecord> search(AuditLogQuery query, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<AuditLogRecord> contentQuery = cb.createQuery(AuditLogRecord.class);
        Root<AuditLogRecord> root = contentQuery.from(AuditLogRecord.class);
        contentQuery.where(predicates(cb, root, query));
        contentQuery.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));

        TypedQuery<AuditLogRecord> typedQuery = entityManager.createQuery(contentQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());
        List<AuditLogRecord> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<AuditLogRecord> countRoot = countQuery.from(AuditLogRecord.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countRoot, query));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<AuditLogRecord> root, AuditLogQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        if (query.actorUserId() != null) {
            predicates.add(cb.equal(root.get("actorUserId"), query.actorUserId()));
        }
        if (query.action() != null && !query.action().isBlank()) {
            predicates.add(cb.equal(root.get("action"), query.action()));
        }
        if (query.resourceType() != null && !query.resourceType().isBlank()) {
            predicates.add(cb.equal(root.get("resourceType"), query.resourceType()));
        }
        if (query.resourceId() != null && !query.resourceId().isBlank()) {
            predicates.add(cb.equal(root.get("resourceId"), query.resourceId()));
        }
        if (query.riskLevel() != null) {
            predicates.add(cb.equal(root.get("riskLevel"), query.riskLevel()));
        }
        if (query.from() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
        }
        if (query.to() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), query.to()));
        }
        return predicates.toArray(new Predicate[0]);
    }
}