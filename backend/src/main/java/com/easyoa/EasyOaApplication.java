package com.easyoa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * EasyOA API 应用入口。
 *
 * <p>EasyOA 是面向学生团队、小公司、工作室的私有化协同办公系统（单组织、单实例部署）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EasyOaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyOaApplication.class, args);
    }
}