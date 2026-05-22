package com.niro.core.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "niro.security")
public class SecurityProperties {

    private List<String> allowedOrigins = List.of("http://localhost:5173");
}
