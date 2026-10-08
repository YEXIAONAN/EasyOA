package com.easyoa.project.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.project.domain.ProjectMember;
import com.easyoa.project.domain.ProjectRole;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProjectId(Long projectId);

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    Optional<ProjectMember> findFirstByProjectIdAndRole(Long projectId, ProjectRole role);

    long countByProjectId(Long projectId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /** 项目成员（含用户信息，按角色排序：OWNER → DEPUTY → MEMBER）。 */
    @Query("""
            select m from ProjectMember m join fetch m.user
            where m.project.id = :projectId
            order by m.role asc, m.joinedAt asc
            """)
    List<ProjectMember> findByProjectIdWithUser(@Param("projectId") Long projectId);

    /** 批量加载多个项目的成员（列表页避免 N+1）。 */
    @Query("""
            select m from ProjectMember m join fetch m.user
            where m.project.id in :projectIds
            order by m.role asc
            """)
    List<ProjectMember> findByProjectIdsWithUser(@Param("projectIds") Collection<Long> projectIds);

    /** 用户参与的项目 ID（用于数据范围判断）。 */
    @Query("select m.project.id from ProjectMember m where m.user.id = :userId")
    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);

    /** 指定用户的项目参与记录（含项目信息，用于成员档案的「参与项目」区块）。 */
    @Query("""
            select m from ProjectMember m join fetch m.project
            where m.user.id = :userId
            """)
    List<ProjectMember> findByUserIdWithProject(@Param("userId") Long userId);
}