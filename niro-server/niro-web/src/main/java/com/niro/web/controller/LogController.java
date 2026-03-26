package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.core.exception.BusinessException;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统日志接口
 *
 * @author liyl
 * @since 2025-12-24
 */
@Tag(name = "系统日志")
@RestController
@RequestMapping("/log")
@Slf4j
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /**
     * 根据 TraceID 查询日志
     */
    @GetMapping("/search")
    @SaCheckPermission(PermissionConstants.LOG_LIST)
    @Operation(summary = "全链路日志查询")
    public List<Map<String, Object>> searchLogs(@RequestParam String traceId) {
        return logService.queryLogsByTraceId(traceId);
    }

    @GetMapping("/stream")
    @SaCheckPermission(PermissionConstants.LOG_LIST)
    @Operation(summary = "实时日志流 (SSE)")
    public void streamLogs() {
        log.info("实时日志流接口已下线，简化版不再提供 Spider 日志推送");
        throw new BusinessException("简化版已移除实时日志流，请使用 TraceId 查询日志");
    }
}
