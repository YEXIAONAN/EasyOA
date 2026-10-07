package com.easyoa.organization.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.organization.domain.OrgUnit;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, Long> {

    List<OrgUnit> findAllByOrderByParentIdAscSortOrderAscNameAsc();

    List<OrgUnit> findByParentIdOrderBySortOrderAscNameAsc(Long parentId);

    long countByParentIdAndStatus(Long parentId, com.easyoa.organization.domain.OrgUnitStatus status);

    @Query("select u.id from OrgUnit u where u.managerUserId = :managerUserId")
    List<Long> findIdsByManagerUserId(@Param("managerUserId") Long managerUserId);

    /**
     * 子树单元 ID（含自身）—— PostgreSQL {@code WITH RECURSIVE}。
     *
     * <p>用于「移动组织时禁止成环」与「负责人可管理范围」判定。
     */
    @Query(value = """
            with recursive subtree as (
                select id from org_units where id = :rootId
                union all
                select child.id from org_units child join subtree parent on child.parent_id = parent.id
            )
            select id from subtree
            """, nativeQuery = true)
    List<Long> findSubtreeIds(@Param("rootId") Long rootId);
}