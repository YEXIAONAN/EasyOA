package com.easyoa.search.dto;

import java.util.List;

/**
 * 全局搜索结果（按类别分组，每组最多 5 条）。
 *
 * <p>v0.1.0 使用 PostgreSQL 能力（ILIKE 前缀匹配），不引入 Elasticsearch。
 */
public record SearchResponse(
        String query,
        List<SearchHit> projects,
        List<SearchHit> tasks,
        List<SearchHit> users,
        List<SearchHit> approvals) {
}