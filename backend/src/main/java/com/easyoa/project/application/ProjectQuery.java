package com.easyoa.project.application;

import com.easyoa.project.domain.ProjectStatus;

/**
 * 项目列表查询条件。
 */
public record ProjectQuery(String keyword, ProjectStatus status, int page, int size) {

    public static ProjectQuery of(String keyword, ProjectStatus status, Integer page, Integer size) {
        String normalized = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return new ProjectQuery(normalized, status, page == null ? 1 : page,
                size == null ? 12 : Math.min(size, 100));
    }
}