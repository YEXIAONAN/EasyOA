package com.easyoa.securityevent.repository;

import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.easyoa.securityevent.domain.SecurityEvent;

/**
 * 安全事件仓储：只允许写入与读取，不存在 delete 能力。
 *
 * <p>检索能力通过自定义只读片段 {@link SecurityEventSearchFragment} 提供，
 * 而不是继承 {@code JpaSpecificationExecutor}（后者会暴露 delete）。
 */
public interface SecurityEventRepository extends Repository<SecurityEvent, Long>, SecurityEventSearchFragment {

    SecurityEvent save(SecurityEvent event);

    Optional<SecurityEvent> findTopByOrderByIdDesc();

    long count();
}