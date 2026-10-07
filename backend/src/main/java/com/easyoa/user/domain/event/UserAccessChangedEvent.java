package com.easyoa.user.domain.event;

/**
 * 用户访问权限发生变化（账号被禁用、系统角色被调整）。
 *
 * <p>该事件用于让认证模块撤销该用户的全部会话：
 * 会话中缓存的角色信息必须失效，否则被降级的用户会保留原有权限直到登出。
 */
public record UserAccessChangedEvent(Long userId, String reason) {
}