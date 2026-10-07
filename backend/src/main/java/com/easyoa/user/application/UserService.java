package com.easyoa.user.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.dto.UserBrief;
import com.easyoa.user.dto.UserProfileResponse;
import com.easyoa.user.repository.UserRepository;

/**
 * 用户基础能力。仅承载用户主数据读写，权限判断在各自业务模块中执行。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(ApiException::notFound);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long id) {
        return UserProfileResponse.from(getById(id));
    }

    @Transactional(readOnly = true)
    public long countUsers() {
        return userRepository.countAllUsers();
    }

    /** 批量获取用户简要信息（跨模块展示用，避免 N+1）。 */
    @Transactional(readOnly = true)
    public Map<Long, UserBrief> findBriefs(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, UserBrief::from));
    }

    @Transactional(readOnly = true)
    public Optional<UserBrief> findBrief(Long userId) {
        return userRepository.findById(userId).map(UserBrief::from);
    }

    /**
     * 创建用户。用户名唯一性由数据库唯一索引与前置校验双重保证。
     */
    @Transactional
    public User create(String username, String displayName, String passwordHash, SystemRole systemRole) {
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }
        return userRepository.save(new User(username, displayName, passwordHash, systemRole));
    }

    @Transactional
    public void recordLogin(Long userId, Instant at) {
        userRepository.findById(userId).ifPresent(user -> user.recordLogin(at));
    }

    @Transactional
    public void changePassword(Long userId, String newPasswordHash) {
        User user = getById(userId);
        user.changePassword(newPasswordHash, Instant.now());
    }
}