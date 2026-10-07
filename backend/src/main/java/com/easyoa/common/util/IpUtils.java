package com.easyoa.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析。
 *
 * <p>生产部署为 {@code Nginx → Spring Boot}，因此优先读取代理头。
 * 注意：只有在可信反向代理正确覆盖 {@code X-Forwarded-For} 的前提下该值才可信，
 * 项目提供的 Nginx 配置已强制覆盖该头（见 infra/nginx/conf.d/easyoa.conf）。
 */
public final class IpUtils {

    private static final int MAX_LENGTH = 45;

    private IpUtils() {
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 取第一个（最靠近客户端的地址）
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return truncate(realIp.trim());
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() <= MAX_LENGTH ? ip : ip.substring(0, MAX_LENGTH);
    }
}