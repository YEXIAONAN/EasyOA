package com.easyoa.project.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新项目基本信息（OWNER / DEPUTY_OWNER）。
 */
public record UpdateProjectRequest(
        @NotBlank(message = "请输入项目名称")
        @Size(max = 120, message = "项目名称过长")
        String name,

        @Size(max = 2000, message = "项目简介过长")
        String description,

        Instant plannedStartAt,

        Instant plannedEndAt) {
}