package com.easyoa.organization.dto;

import com.easyoa.organization.domain.OrgUnitType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 更新组织单元（PUT：整体提交，managerUserId 为空表示清除负责人）。
 */
public record UpdateOrgUnitRequest(
        @NotBlank(message = "请输入组织单元名称")
        @Size(max = 80, message = "组织单元名称过长")
        String name,

        @NotNull(message = "请选择组织单元类型")
        OrgUnitType type,

        Integer sortOrder,

        Long managerUserId) {
}