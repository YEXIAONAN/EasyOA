package com.easyoa.search.dto;

/**
 * 全局搜索命中项（含深链）。
 */
public record SearchHit(
        Long id,
        String title,
        String subtitle,
        String link) {
}