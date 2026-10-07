package com.easyoa.approval.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.approval.domain.ApprovalNode;

public interface ApprovalNodeRepository extends JpaRepository<ApprovalNode, Long> {

    @Query("""
            select n from ApprovalNode n
            where n.instance.id = :instanceId
            order by n.nodeIndex asc
            """)
    List<ApprovalNode> findByInstanceId(@Param("instanceId") Long instanceId);

    @Query("""
            select n from ApprovalNode n
            where n.instance.id in :instanceIds
            order by n.instance.id asc, n.nodeIndex asc
            """)
    List<ApprovalNode> findByInstanceIds(@Param("instanceIds") Collection<Long> instanceIds);
}