package com.easyoa.approval.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.approval.domain.ApprovalAction;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {

    @Query("""
            select a from ApprovalAction a
            join fetch a.actor
            where a.instance.id = :instanceId
            order by a.createdAt asc, a.id asc
            """)
    List<ApprovalAction> findByInstanceId(@Param("instanceId") Long instanceId);
}