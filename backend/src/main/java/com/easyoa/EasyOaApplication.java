package com.easyoa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EasyOA API 应用入口。
 *
 * <p>EasyOA 是面向学生团队 / 小公司 / 工作室的私有化协同办公系统（单组织、单实例部署）。
 *
 * <p>调度仅用于系统级巡检（如任务期限扫描生成通知），可在
 * {@code easyoa.notifications.scheduler.enabled=false} 下关闭。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class EasyOaApplication {

    public static void main(String[] args) {
        SpringApplication.run(EasyOaApplication.class, args);
    }
}