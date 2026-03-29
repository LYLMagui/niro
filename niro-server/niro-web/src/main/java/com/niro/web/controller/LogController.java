package com.niro.web.controller;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.niro.web.service.LogService;
import com.niro.web.constant.PermissionConstants;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

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
    // 使用 ReactiveRedisTemplate 进行响应式订阅
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Value("${log.path:../../logs/niro_spider.log}")
    private String logPath;

    /**
     * 根据 TraceID 查询日志
     */
    @GetMapping("/search")
    @SaCheckPermission(PermissionConstants.LOG_LIST)
    @Operation(summary = "全链路日志查询")
    public List<Map<String, Object>> searchLogs(@RequestParam String traceId) {
        return logService.queryLogsByTraceId(traceId);
    }

    /**
     * 尝试在不同可能的相对路径下寻找日志文件
     */
    private File findLogFile() {
        // 1. 优先使用配置路径 (Docker 容器内绝对路径)
        if (logPath.startsWith("/")) {
            File configLogFile = new File(logPath);
            log.info("🔍 正在检查配置路径 (Docker/Absolute): {}", configLogFile.getAbsolutePath());
            if (configLogFile.exists()) {
                return configLogFile;
            }
        }

        // 2. 尝试从当前目录向上查找 (开发环境相对路径)
        String userDir = System.getProperty("user.dir");
        log.info("🔍 正在尝试自动定位日志文件 (Relative)，当前工作目录: {}", userDir);
        File current = new File(userDir);
        while (current != null) {
            File spiderLogs = new File(current, "logs/niro_spider.log");
            if (spiderLogs.exists()) {
                log.info("✅ 找到日志文件: {}", spiderLogs.getAbsolutePath());
                return spiderLogs;
            }

            current = current.getParentFile();
        }

        log.warn("❌ 无法定位日志文件。当前工作目录: {}, 配置路径: {}. 请检查 niro-spider 是否已启动并生成日志。", userDir, logPath);
        return null;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckPermission(PermissionConstants.LOG_LIST)
    @Operation(summary = "实时日志流 (SSE)")
    public Flux<ServerSentEvent<String>> streamLogs() {
        // 1. 构建初始快照流 (最后 100 行)
        // 使用 Flux.defer 确保每次订阅都重新读取文件，并使用 boundedElastic 调度器执行阻塞 I/O
        Flux<String> snapshotFlux = Flux.defer(() -> {
            File logFile = findLogFile();
            List<String> lines = new ArrayList<>();
            
            if (logFile != null && logFile.exists()) {
                log.info("📌 正在读取日志文件初始快照: {}", logFile.getAbsolutePath());
                try {
                    lines.addAll(readLastNLines(logFile, 100));
                    
                    // 添加连接成功提示
                    String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                    String successMsg = String.format("%s | INFO     | system.log:connect:0 - traceId: system | ip: 127.0.0.1 | >>> 历史日志加载完毕，开始接收实时推送...", now);
                    lines.add(successMsg);
                } catch (Exception e) {
                    log.error("读取初始日志失败", e);
                    lines.add("读取初始日志失败: " + e.getMessage());
                }
            } else {
                 String currentDir = System.getProperty("user.dir");
                 String errorMsg = String.format("❌ 无法定位日志文件。当前工作目录: %s, 配置路径: %s. 请检查 niro-spider 是否已启动并生成日志。", currentDir, logPath);
                 log.error(errorMsg);
                 lines.add(errorMsg);
            }
            return Flux.fromIterable(lines);
        }).subscribeOn(Schedulers.boundedElastic());

        // 2. 构建 Redis 实时订阅流
        Flux<String> redisFlux = reactiveRedisTemplate.listenTo(ChannelTopic.of("niro:spider:logs"))
                .map(message -> message.getMessage())
                .doOnSubscribe(sub -> log.info("📡 开始订阅 Redis 频道: niro:spider:logs"))
                .doOnCancel(() -> log.info("🔌 SSE 连接断开，已取消 Redis 订阅"))
                .doFinally(signal -> log.debug("Redis subscription signal: {}", signal));

        // 3. 合并流并转换为 ServerSentEvent
        return Flux.concat(snapshotFlux, redisFlux)
                .map(line -> ServerSentEvent.builder(line).build());
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
            boolean firstLineSkipped = false;
            for (String line : rawLines) {
                // 如果 startPos 不是 0，第一行很可能是被截断的残缺行，应该丢弃
                // 除非 bytes 恰好以换行符开始 (content第一个字符为空行或完整行的开始)
                if (startPos > 0 && !firstLineSkipped && rawLines.length > 1) {
                    firstLineSkipped = true;
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
