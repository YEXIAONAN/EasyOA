package com.easyoa.user.application;

import com.easyoa.user.domain.UserStatus;

/**
 * 成员目录查询条件。
 *
 * @param keyword   关键字（用户名 / 姓名 / 职位）
 * @param orgUnitId 组织单元筛选（自动包含其下级单元）；为空表示不过滤
 * @param status    账号状态筛选；为空表示全部
 */
public record UserDirectoryQuery(
        String keyword,
        Long orgUnitId,
        UserStatus status,
        int page,
        int size) {

    public static UserDirectoryQuery of(String keyword, Long orgUnitId, UserStatus status, Integer page, Integer size) {
        String normalized = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return new UserDirectoryQuery(normalized, orgUnitId, status, page == null ? 1 : page,
                size == null ? 24 : Math.min(size, 100));
    }
}