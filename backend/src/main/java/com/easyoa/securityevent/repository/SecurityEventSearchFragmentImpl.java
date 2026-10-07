package com.easyoa.securityevent.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.easyoa.securityevent.domain.SecurityEvent;
import com.easyoa.securityevent.dto.SecurityEventQuery;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * {@link SecurityEventSearchFragment} 的 Criteria API 实现（只读）。
 */
public class SecurityEventSearchFragmentImpl implements SecurityEventSearchFragment {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<SecurityEvent> search(SecurityEventQuery query, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<SecurityEvent> contentQuery = cb.createQuery(SecurityEvent.class);
        Root<SecurityEvent> root = contentQuery.from(SecurityEvent.class);
        contentQuery.where(predicates(cb, root, query));
        contentQuery.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));

        TypedQuery<SecurityEvent> typedQuery = entityManager.createQuery(contentQuery)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());
        List<SecurityEvent> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<SecurityEvent> countRoot = countQuery.from(SecurityEvent.class);
        countQuery.select(cb.count(countRoot)).where(predicates(cb, countRoot, query));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }

    private Predicate[] predicates(CriteriaBuilder cb, Root<SecurityEvent> root, SecurityEventQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        if (query.eventType() != null) {
            predicates.add(cb.equal(root.get("eventType"), query.eventType()));
        }
        if (query.severity() != null && !query.severity().isBlank()) {
            predicates.add(cb.equal(root.get("severity"), query.severity().trim()));
        }
        if (query.actorUserId() != null) {
            predicates.add(cb.equal(root.get("actorUserId"), query.actorUserId()));
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
