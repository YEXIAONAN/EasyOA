package com.easyoa.approval.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.approval.domain.ApprovalInstance;
import com.easyoa.approval.domain.ApprovalStatus;
import com.easyoa.approval.domain.ApproverStatus;

public interface ApprovalInstanceRepository extends JpaRepository<ApprovalInstance, Long> {

    @Query("""
            select i from ApprovalInstance i
            join fetch i.applicant
            join fetch i.template
            join fetch i.templateVersion
            where i.id = :id
            """)
    Optional<ApprovalInstance> findByIdWithDetails(@Param("id") Long id);

    /** 我发起的。 */
    @Query(value = """
            select i from ApprovalInstance i
            join fetch i.applicant
            join fetch i.template
            where i.applicant.id = :userId
            order by i.updatedAt desc, i.id desc
            """,
            countQuery = """
            select count(i) from ApprovalInstance i where i.applicant.id = :userId
            """)
    Page<ApprovalInstance> searchMine(@Param("userId") Long userId, Pageable pageable);

    /** 待我审批：当前节点且我为待处理审批人。 */
    @Query(value = """
            select i from ApprovalInstance i
            join fetch i.applicant
            join fetch i.template
            where i.status = :status
              and exists (
                select n.id from ApprovalNode n
                where n.instance = i and n.nodeIndex = i.currentNodeIndex
                  and exists (
                    select a.id from ApprovalNodeApprover a
                    where a.node = n and a.user.id = :userId and a.status = :approverStatus))
            order by i.updatedAt desc, i.id desc
            """,
            countQuery = """
            select count(i) from ApprovalInstance i
            where i.status = :status
              and exists (
                select n.id from ApprovalNode n
                where n.instance = i and n.nodeIndex = i.currentNodeIndex
                  and exists (
                    select a.id from ApprovalNodeApprover a
                    where a.node = n and a.user.id = :userId and a.status = :approverStatus))
            """)
    Page<ApprovalInstance> searchPendingForApprover(@Param("userId") Long userId,
            @Param("status") ApprovalStatus status, @Param("approverStatus") ApproverStatus approverStatus,
            Pageable pageable);

    /** 已完成：终态且我发起或我参与过审批。 */
    @Query(value = """
            select i from ApprovalInstance i
            join fetch i.applicant
            join fetch i.template
            where i.status in :statuses
              and (i.applicant.id = :userId
                   or exists (select a.id from ApprovalNodeApprover a
                              where a.node.instance = i and a.user.id = :userId))
            order by i.finishedAt desc, i.id desc
            """,
            countQuery = """
            select count(i) from ApprovalInstance i
            where i.status in :statuses
              and (i.applicant.id = :userId
                   or exists (select a.id from ApprovalNodeApprover a
                              where a.node.instance = i and a.user.id = :userId))
            """)
    Page<ApprovalInstance> searchFinishedForParticipant(@Param("userId") Long userId,
            @Param("statuses") Collection<ApprovalStatus> statuses, Pageable pageable);

    /** 工作台 KPI：待我审批数量。 */
    @Query("""
            select count(i) from ApprovalInstance i
            where i.status = :status
              and exists (
                select n.id from ApprovalNode n
                where n.instance = i and n.nodeIndex = i.currentNodeIndex
                  and exists (
                    select a.id from ApprovalNodeApprover a
                    where a.node = n and a.user.id = :userId and a.status = :approverStatus))
            """)
    long countPendingForApprover(@Param("userId") Long userId, @Param("status") ApprovalStatus status,
            @Param("approverStatus") ApproverStatus approverStatus);
}