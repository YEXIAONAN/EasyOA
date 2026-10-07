package com.easyoa.approval.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
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

    /** Activity Feed：最近审批动作（数据范围：申请人 / 参与审批者 / 管理员）。 */
    @Query("""
            select a from ApprovalAction a
            join fetch a.actor
            join fetch a.instance i
            join fetch i.template
            where (:scopeAll = true
                   or i.applicant.id = :userId
                   or exists (select x.id from ApprovalNodeApprover x
                              where x.node.instance = i and x.user.id = :userId))
            order by a.createdAt desc, a.id desc
            """)
    List<ApprovalAction> findRecentForActivity(@Param("scopeAll") boolean scopeAll, @Param("userId") Long userId,
            Pageable pageable);
}