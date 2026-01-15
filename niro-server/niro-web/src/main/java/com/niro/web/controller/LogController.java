package com.niro.web.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.Tailer;
import com.niro.web.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Value("${spider.log.path:../../niro-spider/logs/niro_spider.log}")
    private String logPath;

    /**
     * 根据 TraceID 查询日志
     */
    @GetMapping("/search")
    @Operation(summary = "全链路日志查询")
    public List<Map<String, Object>> searchLogs(@RequestParam String traceId) {
        return logService.queryLogsByTraceId(traceId);
    }

    /**
     * 尝试在不同可能的相对路径下寻找日志文件
     */
    private File findLogFile() {
        String userDir = System.getProperty("user.dir");
        log.info("🔍 正在尝试定位日志文件，当前工作目录: {}", userDir);

        // 1. 尝试配置的路径
        File file = new File(logPath);
        if (!file.isAbsolute()) {
            file = new File(userDir, logPath);
        }
        log.info("尝试路径 1 (配置路径): {}", file.getAbsolutePath());
        if (file.exists()) return file;

        // 2. 尝试从当前目录向上查找直到找到包含 niro-spider 的目录
        File current = new File(userDir);
        while (current != null) {
            File spiderLogs = new File(current, "niro-spider/logs/niro_spider.log");
            log.info("尝试路径 (向上查找): {}", spiderLogs.getAbsolutePath());
            if (spiderLogs.exists()) return spiderLogs;
            
            // 兼容可能直接在 niro-spider 目录下的情况
            File directLogs = new File(current, "logs/niro_spider.log");
            if (directLogs.exists() && current.getName().equals("niro-spider")) return directLogs;
            
            current = current.getParentFile();
        }

        return null;
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "实时日志流 (SSE)")
    public SseEmitter streamLogs() {
        // 设置较长的超时时间 (例如 30 分钟)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        // 尝试多种可能的路径来定位日志文件
        File logFile = findLogFile();
        
        if (logFile == null || !logFile.exists()) {
            try {
                String currentDir = System.getProperty("user.dir");
                String errorMsg = String.format("❌ 无法定位日志文件。当前工作目录: %s, 配置路径: %s. 请检查 niro-spider 是否已启动并生成日志。", currentDir, logPath);
                emitter.send(SseEmitter.event().data(errorMsg));
                log.error(errorMsg);
            } catch (Exception e) {
                log.error("SSE发送失败", e);
            }
            return emitter;
        }

        log.info("📌 正在读取日志文件: {}", logFile.getAbsolutePath());

        // 使用 Hutool Tailer 监听文件末尾 (读取最后50行)
        Tailer tailer = new Tailer(logFile, StandardCharsets.UTF_8, line -> {
            try {
                emitter.send(SseEmitter.event().data(line));
            } catch (Exception e) {
                // 发送失败通常意味着客户端断开，抛出异常以停止 Tailer
                throw new RuntimeException("Client disconnected", e);
            }
        }, 50, 1000);

        // 异步启动 Tailer
        executor.execute(() -> {
            try {
                // 先发送一条连接成功消息
                emitter.send(SseEmitter.event().data(">>> 连接日志服务成功，正在读取实时日志..."));
                tailer.start();
            } catch (Exception e) {
                // 忽略 "Client disconnected" 异常
                if (!"Client disconnected".equals(e.getMessage())) {
                    log.warn("Tailer stopped: {}", e.getMessage());
                }
            }
        });
        
        // 客户端断开或超时时停止 Tailer
        Runnable stopTask = () -> {
            try {
                tailer.stop();
            } catch (Exception e) {
                log.error("Stop tailer error", e);
            }
        };

        emitter.onCompletion(stopTask);
        emitter.onTimeout(stopTask);
        emitter.onError((e) -> stopTask.run());

        return emitter;
    }
}
