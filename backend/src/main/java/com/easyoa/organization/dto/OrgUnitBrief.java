package com.easyoa.organization.dto;

import com.easyoa.organization.domain.OrgUnit;

/**
 * 组织单元简要信息（用于成员档案「主部门 / 其他组织」展示）。
 */
public record OrgUnitBrief(Long id, String name, String type, String status) {

    public static OrgUnitBrief from(OrgUnit unit) {
        return new OrgUnitBrief(unit.getId(), unit.getName(), unit.getType().name(), unit.getStatus().name());
    }
}