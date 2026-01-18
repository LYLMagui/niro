package com.niro.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.niro.web.service.BuffScanTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "系统健康检查", description = "系统监控相关接口")
@RequiredArgsConstructor
public class HealthController {

    private final BuffScanTaskService buffScanTaskService;

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查系统是否正常运行")
    public String health() {
        return "OK";
    }

    @GetMapping("/test/start_task_6")
    public String startTask6() {
        buffScanTaskService.updateStatus(6L, 1);
        return "Task 6 started";
    }
}