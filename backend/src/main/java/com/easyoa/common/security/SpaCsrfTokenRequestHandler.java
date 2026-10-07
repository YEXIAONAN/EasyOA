package com.easyoa.common.security;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SPA（Vue + Axios）CSRF 处理：
 *
 * <ul>
 *   <li>渲染到请求属性（供前后端同源调试 / Swagger）时使用 XOR 编码，防 BREACH 攻击；</li>
 *   <li>来自 SPA 的真实请求头（X-XSRF-TOKEN，读取自非 HttpOnly 的 XSRF-TOKEN Cookie）使用明文校验。</li>
 * </ul>
 *
 * <p>参考 Spring Security 官方 SPA 集成方案。
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // 触发 Token 加载，确保 Cookie 被写入响应
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        if (StringUtils.hasText(headerValue)) {
            return plain.resolveCsrfTokenValue(request, csrfToken);
        }
        return xor.resolveCsrfTokenValue(request, csrfToken);
    }
}