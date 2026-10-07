package com.easyoa.common.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.easyoa.user.domain.SystemRole;

/**
 * 认证主体（存入 SecurityContext 的最小信息集）。
 *
 * <p>刻意不将 JPA 实体放入会话：只保留鉴权所需字段，避免实体被序列化与脏写。
 */
public class SecurityUser implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String displayName;
    private final SystemRole systemRole;
    private final boolean totpEnabled;
    private final boolean active;
    private final String passwordHash;

    public SecurityUser(Long id, String username, String displayName, SystemRole systemRole, boolean totpEnabled,
            boolean active, String passwordHash) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.systemRole = systemRole;
        this.totpEnabled = totpEnabled;
        this.active = active;
        this.passwordHash = passwordHash;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public SystemRole systemRole() {
        return systemRole;
    }

    public boolean totpEnabled() {
        return totpEnabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + systemRole.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}