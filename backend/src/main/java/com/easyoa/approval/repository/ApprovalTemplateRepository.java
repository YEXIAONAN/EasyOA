package com.easyoa.approval.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.approval.domain.ApprovalTemplate;

public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {

    Optional<ApprovalTemplate> findByNameIgnoreCase(String name);

    @Query(value = """
            select t from ApprovalTemplate t
            where (:includeDisabled = true or t.enabled = true)
              and (:keyword is null or lower(t.name) like :keyword or lower(coalesce(t.description, '')) like :keyword)
            order by t.updatedAt desc, t.id desc
            """,
            countQuery = """
            select count(t) from ApprovalTemplate t
            where (:includeDisabled = true or t.enabled = true)
              and (:keyword is null or lower(t.name) like :keyword or lower(coalesce(t.description, '')) like :keyword)
            """)
    Page<ApprovalTemplate> search(@Param("includeDisabled") boolean includeDisabled,
            @Param("keyword") String keyword, Pageable pageable);
}