package com.easyoa.securityevent.repository;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.easyoa.securityevent.domain.SecurityEvent;

/**
 * 安全事件仓储：只允许写入与读取，不存在 delete 能力。
 */
public interface SecurityEventRepository extends Repository<SecurityEvent, Long> {

    SecurityEvent save(SecurityEvent event);

    Optional<SecurityEvent> findTopByOrderByIdDesc();

    long count();
}