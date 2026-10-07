package com.easyoa.approval.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.approval.domain.ApprovalTemplateVersion;

public interface ApprovalTemplateVersionRepository extends JpaRepository<ApprovalTemplateVersion, Long> {

    List<ApprovalTemplateVersion> findByTemplateIdOrderByVersionNoDesc(Long templateId);

    Optional<ApprovalTemplateVersion> findByIdAndTemplateId(Long id, Long templateId);

    @Query("select coalesce(max(v.versionNo), 0) from ApprovalTemplateVersion v where v.template.id = :templateId")
    int maxVersionNo(@Param("templateId") Long templateId);
}