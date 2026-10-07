package com.easyoa.approval.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.approval.domain.ApprovalNodeApprover;

public interface ApprovalNodeApproverRepository extends JpaRepository<ApprovalNodeApprover, Long> {

    @Query("""
            select a from ApprovalNodeApprover a
            join fetch a.user
            where a.node.id in :nodeIds
            order by a.id asc
            """)
    List<ApprovalNodeApprover> findByNodeIds(@Param("nodeIds") Collection<Long> nodeIds);

    Optional<ApprovalNodeApprover> findByNodeIdAndUserId(Long nodeId, Long userId);

    /** 是否参与过某实例的审批（数据范围判定）。 */
    @Query("""
            select count(a) > 0 from ApprovalNodeApprover a
            where a.node.instance.id = :instanceId and a.user.id = :userId
            """)
    boolean existsByInstanceAndUser(@Param("instanceId") Long instanceId, @Param("userId") Long userId);

    @Query("""
            select a from ApprovalNodeApprover a
            join fetch a.user
            where a.node.instance.id = :instanceId
            order by a.id asc
            """)
    List<ApprovalNodeApprover> findByInstanceId(@Param("instanceId") Long instanceId);
}