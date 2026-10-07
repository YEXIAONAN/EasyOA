package com.easyoa.system.dto;

/**
 * 首次初始化状态（公开接口，不泄露任何敏感信息）。
 */
public record SetupStatusResponse(boolean required, String organizationName) {
}