package com.easyoa.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import com.easyoa.auth.security.SessionValidationFilter;

/**
 * Spring Security 配置：Session Authentication（HttpOnly Cookie）+ CSRF 防护。
 *
 * <p>安全决策：
 * <ul>
 *   <li>不使用 JWT / localStorage，认证状态只存在于 HttpOnly 会话 Cookie；</li>
 *   <li>所有写请求强制 CSRF（Cookie + Header 双提交，XOR 编码防 BREACH）；</li>
 *   <li>未认证 → 401 JSON；无权限 → 403 JSON，永不重定向到登录页 HTML；</li>
 *   <li>Swagger / OpenAPI 仅开发环境启用（生产 profile 中关闭）。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            SessionValidationFilter sessionValidationFilter) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 首次初始化（受原子开关保护，完成后自动失效）
                        .requestMatchers(HttpMethod.GET, "/api/setup/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/setup/initialize").permitAll()
                        // 认证入口
                        .requestMatchers("/api/auth/login", "/api/auth/logout", "/api/csrf").permitAll()
                        // 健康检查（容器编排使用）
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // API 文档（生产环境通过配置关闭）
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 其余一切必须认证
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterAfter(sessionValidationFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** DelegatingPasswordEncoder：默认 BCrypt，未来可平滑升级 Argon2（哈希自带算法前缀）。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}