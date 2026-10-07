package com.easyoa.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.security.SecurityUser;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;

/**
 * Spring Security 用户加载器。
 */
@Service
public class SecurityUserService implements UserDetailsService {

    private final UserRepository userRepository;

    public SecurityUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
        return new SecurityUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getSystemRole(),
                user.isTotpEnabled(),
                user.isActive(),
                user.getPasswordHash());
    }
}