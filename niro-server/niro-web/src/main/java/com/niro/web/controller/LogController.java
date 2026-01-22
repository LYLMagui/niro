package com.niro.web.controller;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.niro.web.service.LogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final StringRedisTemplate stringRedisTemplate;

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

        // 尝试从当前目录向上查找直到找到包含 niro-spider 的目录
        File current = new File(userDir);
        while (current != null) {
            File spiderLogs = new File(current, "niro-spider/logs/niro_spider.log");
            log.info("尝试路径 (向上查找 LOG): {}", spiderLogs.getAbsolutePath());
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

        log.info("📌 正在读取日志文件初始快照: {}", logFile.getAbsolutePath());

        // 1. 发送初始快照 (最后 100 行)
        try {
            List<String> lastLines = readLastNLines(logFile, 100);
            for (String line : lastLines) {
                emitter.send(SseEmitter.event().data(line));
            }
            // 发送一条连接成功消息
            String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            String successMsg = String.format("%s | INFO     | system.log:connect:0 - traceId: system | ip: 127.0.0.1 | >>> 历史日志加载完毕，开始接收实时推送...", now);
            emitter.send(SseEmitter.event().data(successMsg));
        } catch (Exception e) {
            log.error("读取初始日志失败", e);
        }

        // 2. 启动 Redis 订阅 (异步)
        AtomicReference<RedisConnection> connectionRef = new AtomicReference<>();
        
        executor.execute(() -> {
            try {
                // 获取原始连接进行订阅 (阻塞操作)
                stringRedisTemplate.execute((RedisConnection connection) -> {
                    connectionRef.set(connection);
                    log.info("📡 开始订阅 Redis 频道: niro:spider:logs");
                    
                    connection.subscribe((message, pattern) -> {
                        try {
                            String body = new String(message.getBody(), StandardCharsets.UTF_8);
                            // 实时推送
                            emitter.send(SseEmitter.event().data(body));
                        } catch (IOException e) {
                            // 客户端断开，抛出异常中断订阅
                            throw new RuntimeException("Client disconnected", e);
                        }
                    }, "niro:spider:logs".getBytes(StandardCharsets.UTF_8));
                    
                    return null;
                });
            } catch (Exception e) {
                // 忽略 "Client disconnected" 造成的异常
                if (e.getMessage() != null && !e.getMessage().contains("Client disconnected")) {
                     log.warn("Redis subscription stopped: {}", e.getMessage());
                }
            }
        });
        
        // 3. 资源释放
        Runnable stopTask = () -> {
            try {
                RedisConnection c = connectionRef.get();
                if (c != null && !c.isClosed()) {
                    c.close(); // 强制关闭连接以中断 subscribe
                    log.info("🔌 SSE 连接断开，已取消 Redis 订阅");
                }
            } catch (Exception e) {
                log.error("Stop subscription error", e);
            }
        };

        emitter.onCompletion(stopTask);
        emitter.onTimeout(stopTask);
        emitter.onError((e) -> stopTask.run());

        return emitter;
    }

    /**
     * 读取文件最后 N 行 (支持 UTF-8)
     */
    private List<String> readLastNLines(File file, int numLines) {
        List<String> lines = new ArrayList<>();
        if (numLines <= 0) return lines;

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length == 0) return lines;

            // 估算：假设一行平均 200 字节，100 行大约 20KB。
            // 为了安全起见，我们预读多一点，比如 50KB。
            // 如果 50KB 不够 100 行，我们目前简化处理，只返回这 50KB 里的行。
            // 实际场景中，日志一行通常不会特别长，50KB 足够包含最后 100 行。
            int bufferSize = 50 * 1024; // 50KB
            long startPos = Math.max(0, length - bufferSize);
            
            // 移动指针并读取字节
            raf.seek(startPos);
            byte[] bytes = new byte[(int) (length - startPos)];
            raf.readFully(bytes);
            
            // 整体解码为字符串
            String content = new String(bytes, StandardCharsets.UTF_8);
            
            // 按换行符分割
            // 这里的正则 split 可能会消耗一些性能，但对于 50KB 数据是可以接受的
            String[] rawLines = content.split("\r?\n");
            
            // 过滤空行并添加到结果列表
            for (String line : rawLines) {
                // 如果 startPos 不是 0，第一行很可能是被截断的残缺行，应该丢弃
                // 除非 bytes 恰好以换行符开始 (content第一个字符为空行或完整行的开始)
                // 简单起见：如果不是从文件头读取，且 split 结果超过 1 行，丢弃第一行
                if (startPos > 0 && lines.isEmpty() && rawLines.length > 1) {
                    continue; 
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            
            // 截取最后 N 行
            if (lines.size() > numLines) {
                return lines.subList(lines.size() - numLines, lines.size());
            }
            return lines;
            
        } catch (IOException e) {
            log.error("读取日志文件失败", e);
        }
        return lines;
    }
}
