package com.easyoa.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countBySystemRole(SystemRole systemRole);

    /** 系统是否已存在任何用户（用于首启初始化判定）。 */
    @Query("select count(u) from User u")
    long countAllUsers();
}