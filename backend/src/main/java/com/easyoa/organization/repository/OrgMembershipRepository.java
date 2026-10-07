package com.easyoa.organization.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.organization.domain.OrgMembership;

public interface OrgMembershipRepository extends JpaRepository<OrgMembership, Long> {

    List<OrgMembership> findByUserId(Long userId);

    Optional<OrgMembership> findByUserIdAndOrgUnitId(Long userId, Long orgUnitId);

    Optional<OrgMembership> findFirstByUserIdAndPrimaryTrue(Long userId);

    long countByOrgUnitId(Long orgUnitId);

    long countByOrgUnitIdIn(Collection<Long> orgUnitIds);

    boolean existsByOrgUnitId(Long orgUnitId);

    /** 各单元成员数（单次分组查询，供组织树展示）。 */
    @Query("select new com.easyoa.organization.dto.OrgUnitMemberCount(m.orgUnit.id, count(m)) "
            + "from OrgMembership m group by m.orgUnit.id")
    List<com.easyoa.organization.dto.OrgUnitMemberCount> countGroupedByOrgUnit();

    /** 单元成员（含用户，供成员列表展示，避免 N+1）。 */
    @Query("select m from OrgMembership m join fetch m.user where m.orgUnit.id = :orgUnitId order by m.primary desc, m.joinedAt")
    List<OrgMembership> findByOrgUnitIdWithUser(@Param("orgUnitId") Long orgUnitId);

    /** 单个用户的全部归属（含组织单元）。 */
    @Query("select m from OrgMembership m join fetch m.orgUnit where m.user.id = :userId order by m.primary desc, m.orgUnit.sortOrder")
    List<OrgMembership> findByUserIdWithOrgUnit(@Param("userId") Long userId);

    /** 批量加载多个用户的归属（成员目录避免 N+1）。 */
    @Query("select m from OrgMembership m join fetch m.orgUnit where m.user.id in :userIds")
    List<OrgMembership> findByUserIdsWithOrgUnit(@Param("userIds") Collection<Long> userIds);

    /** 按主部门过滤用户（成员目录筛选）。 */
    @Query("select m.user.id from OrgMembership m where m.orgUnit.id in :orgUnitIds")
    List<Long> findUserIdsByOrgUnitIds(@Param("orgUnitIds") Collection<Long> orgUnitIds);
}