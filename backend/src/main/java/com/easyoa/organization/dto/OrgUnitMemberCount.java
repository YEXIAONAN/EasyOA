package com.easyoa.organization.dto;

/**
 * 组织单元成员数量投影（单次分组查询，避免逐单元统计造成的 N+1）。
 */
public record OrgUnitMemberCount(Long orgUnitId, long memberCount) {
}