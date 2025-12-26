package com.niro.web.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.Tailer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
public class LogController {

    @Value("${spider.log.path:../../niro-spider/logs/niro_spider.log}")
    private String logPath;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "实时日志流 (SSE)")
    public SseEmitter streamLogs() {
        // 设置较长的超时时间 (例如 30 分钟)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        File logFile = FileUtil.file(logPath);
        if (!logFile.exists()) {
            try {
                // 发送一条错误信息后结束
                emitter.send(SseEmitter.event().data("日志文件不存在: " + logFile.getAbsolutePath()));
            } catch (Exception e) {
                log.error("SSE发送失败", e);
            }
            return emitter;
        }

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
