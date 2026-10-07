package com.easyoa.user.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.domain.UserStatus;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countBySystemRole(SystemRole systemRole);

    long countBySystemRoleAndStatus(SystemRole systemRole, UserStatus status);

    /** 指定系统角色的有效用户（审批动态审批人 SYSTEM_ROLE 解析）。 */
    @Query("""
            select u from User u
            where u.systemRole = :role and u.status = :status
            order by u.id asc
            """)
    java.util.List<User> findActiveBySystemRole(@Param("role") SystemRole role, @Param("status") UserStatus status);

    /** 系统是否已存在任何用户（用于首启初始化判定）。 */
    @Query("select count(u) from User u")
    long countAllUsers();

    /**
     * 成员目录检索：关键字（用户名 / 姓名 / 职位）+ 状态 + 组织范围。
     *
     * <p>组织范围由调用方展开为单元 ID 集合（含下级单元）；为空集合时由调用方直接返回空页。
     */
    @Query(value = """
            select u from User u
            where (:keyword is null
                   or lower(u.username) like :keyword
                   or lower(u.displayName) like :keyword
                   or lower(coalesce(u.jobTitle, '')) like :keyword)
              and (:status is null or u.status = :status)
              and (:filterByOrg = false or u.id in :userIds)
            order by u.displayName asc, u.id asc
            """,
            countQuery = """
            select count(u) from User u
            where (:keyword is null
                   or lower(u.username) like :keyword
                   or lower(u.displayName) like :keyword
                   or lower(coalesce(u.jobTitle, '')) like :keyword)
              and (:status is null or u.status = :status)
              and (:filterByOrg = false or u.id in :userIds)
            """)
    Page<User> searchDirectory(@Param("keyword") String keyword, @Param("status") UserStatus status,
            @Param("filterByOrg") boolean filterByOrg, @Param("userIds") Collection<Long> userIds,
            Pageable pageable);

    /** 全局搜索：按用户名 / 姓名 / 职位检索有效成员。 */
    @Query("""
            select u from User u
            where u.status = :status
              and (lower(u.username) like :keyword
                   or lower(u.displayName) like :keyword
                   or lower(coalesce(u.jobTitle, '')) like :keyword)
            order by u.displayName asc, u.id asc
            """)
    java.util.List<User> searchByKeyword(@Param("keyword") String keyword, @Param("status") UserStatus status,
            Pageable pageable);
}