package com.easyoa.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新本人档案（成员不能自行修改职位与系统角色）。
 */
public record UpdateMyProfileRequest(
        @NotBlank(message = "请输入显示名称")
        @Size(max = 64, message = "显示名称过长")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 190, message = "邮箱过长")
        String email,

        @Size(max = 32, message = "手机号过长")
        String phone,

        @Size(max = 500, message = "个人简介过长")
        String bio,

        @Size(max = 500, message = "头像地址过长")
        String avatarUrl) {
}