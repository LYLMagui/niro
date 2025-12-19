package com.niro.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan(basePackages = {"com.niro.web.**.mapper"})
@ComponentScan(basePackages = {"com.niro"})
public class BuffWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuffWebApplication.class, args);
    }
}