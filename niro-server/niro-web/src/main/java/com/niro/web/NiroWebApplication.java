package com.niro.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages = {"com.niro.web.**.mapper"})
@ComponentScan(basePackages = {"com.niro"})
public class NiroWebApplication {

    @jakarta.annotation.Resource
    private org.springframework.core.env.Environment env;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 设置默认时区为上海 (GMT+8)
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(NiroWebApplication.class, args);
    }
}