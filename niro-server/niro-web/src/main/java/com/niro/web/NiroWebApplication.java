package com.niro.web;

import jakarta.annotation.PostConstruct;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.TimeZone;

@SpringBootApplication
@MapperScan(basePackages = {"com.niro.web.**.mapper"})
@ComponentScan(basePackages = {"com.niro"})
public class NiroWebApplication {

    @PostConstruct
    public void init() {
        // 设置默认时区为上海 (GMT+8)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(NiroWebApplication.class, args);
    }
}