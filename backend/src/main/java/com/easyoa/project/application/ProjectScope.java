package com.easyoa.project.application;

import java.util.Set;

/**
 * 查看者的项目数据范围。
 *
 * <p>刻意不使用 {@code null} 表达「不受限」：那是极易被误用的隐式约定
 * （一次忘记判空就会越权返回全部项目）。管理员场景用 {@link #all()}
 * 显式表达，业务代码必须通过 {@link #covers(Long)} 判断可见性。
 */
public record ProjectScope(boolean unrestricted, Set<Long> projectIds) {

    /** 系统管理员：可查看全部项目。 */
    public static ProjectScope all() {
        return new ProjectScope(true, Set.of());
    }

    /** 普通成员：仅可见指定项目。 */
    public static ProjectScope of(Set<Long> projectIds) {
        return new ProjectScope(false, Set.copyOf(projectIds));
    }

    public boolean covers(Long projectId) {
        return unrestricted || (projectId != null && projectIds.contains(projectId));
    }

    /** 普通成员且没有任何可见项目（用于提前返回空结果，避免生成非法的空 IN 查询）。 */
    public boolean isEmpty() {
        return !unrestricted && projectIds.isEmpty();
    }
}