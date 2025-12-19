package com.niro.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "系统健康检查", description = "系统监控相关接口")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查系统是否正常运行")
    public String health() {
        return "OK";
    }
}