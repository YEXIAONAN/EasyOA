package com.easyoa.securityevent.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.easyoa.securityevent.domain.SecurityEvent;
import com.easyoa.securityevent.dto.SecurityEventQuery;

/**
 * 安全事件的只读检索能力（自定义片段）。
 *
 * <p>刻意不使用 {@code JpaSpecificationExecutor}：Spring Data JPA 3.x 的该接口会
 * 暴露 {@code delete(Specification)}，从而在类型层面破坏「永久追加」约束
 * （见 {@code SecurityBoundaryIntegrationTest}）。
 */
public interface SecurityEventSearchFragment {

    Page<SecurityEvent> search(SecurityEventQuery query, Pageable pageable);
}
