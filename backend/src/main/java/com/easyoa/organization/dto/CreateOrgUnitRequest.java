package com.easyoa.organization.dto;

import com.easyoa.organization.domain.OrgUnitType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新建组织单元。
 */
public record CreateOrgUnitRequest(
        @NotBlank(message = "请输入组织单元名称")
        @Size(max = 80, message = "组织单元名称过长")
        String name,

        @NotNull(message = "请选择组织单元类型")
        OrgUnitType type,

        /** 上级单元；为空表示顶层单元。 */
        Long parentId,

        Integer sortOrder,

        /** 负责人（可空）。 */
        Long managerUserId) {
}