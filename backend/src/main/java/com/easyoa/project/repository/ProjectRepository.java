package com.easyoa.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.project.domain.Project;
import com.easyoa.project.domain.ProjectStatus;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 项目列表（数据范围由后端计算）：
     * 管理员可见全部项目，普通用户仅可见自己参与的项目。
     */
    @Query(value = """
            select p from Project p
            where (:scopeAll = true or exists (
                    select m.id from ProjectMember m where m.project.id = p.id and m.user.id = :userId))
              and (:status is null or p.status = :status)
              and (:keyword is null
                   or lower(p.name) like :keyword
                   or lower(coalesce(p.description, '')) like :keyword)
            order by p.updatedAt desc, p.id desc
            """,
            countQuery = """
            select count(p) from Project p
            where (:scopeAll = true or exists (
                    select m.id from ProjectMember m where m.project.id = p.id and m.user.id = :userId))
              and (:status is null or p.status = :status)
              and (:keyword is null
                   or lower(p.name) like :keyword
                   or lower(coalesce(p.description, '')) like :keyword)
            """)
    Page<Project> search(@Param("scopeAll") boolean scopeAll,
            @Param("userId") Long userId,
            @Param("status") ProjectStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 用户在指定状态下的项目数量（工作台 KPI）。 */
    @Query("""
            select count(distinct m.project.id) from ProjectMember m
            where m.user.id = :userId and m.project.status = :status
            """)
    long countByUserAndStatus(@Param("userId") Long userId, @Param("status") ProjectStatus status);

    /** 用户参与的最近项目（工作台「项目进度」区块）。 */
    @Query("""
            select distinct p from ProjectMember m join m.project p
            where m.user.id = :userId and p.status <> :excluded
            order by p.updatedAt desc
            """)
    List<Project> findRecentForUser(@Param("userId") Long userId, @Param("excluded") ProjectStatus excluded,
            Pageable pageable);

    Optional<Project> findFirstByNameIgnoreCase(String name);
}