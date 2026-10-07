package com.easyoa.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * OpenAPI 文档（仅开发环境可用；生产环境通过 springdoc.api-docs.enabled=false 关闭）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI easyOaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EasyOA API")
                .version("v0.1.0")
                .description("EasyOA — 面向学生团队 / 小公司 / 工作室的私有化协同办公系统 API。"
                        + "认证方式：Session Cookie（HttpOnly）+ CSRF Token（X-XSRF-TOKEN）。")
                .contact(new Contact().name("EasyOA Team"))
                .license(new License().name("MIT")));
    }
}