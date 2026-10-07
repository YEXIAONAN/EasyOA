package com.easyoa.approval.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新模板基础信息（名称 / 说明 / 启用状态；表单与节点通过发布新版本修改）。
 */
public record UpdateTemplateRequest(
        @NotBlank(message = "请输入模板名称")
        @Size(max = 120, message = "模板名称过长")
        String name,

        @Size(max = 1000, message = "模板说明过长")
        String description,

        Boolean enabled) {
}