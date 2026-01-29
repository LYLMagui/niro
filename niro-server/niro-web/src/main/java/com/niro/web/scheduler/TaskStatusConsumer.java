package com.niro.web.scheduler;

import cn.hutool.json.JSONUtil;
import com.niro.core.constant.BuffConstant;
import com.niro.web.entity.BuffScanTask;
import com.niro.web.service.BuffScanTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Redis 任务状态消息监听器
 * 替换原有的 HTTP 回调方式，提升系统鲁棒性
 *
 * @author niro
 * @since 2026-01-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskStatusConsumer implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final BuffScanTaskService buffScanTaskService;

    private volatile boolean running = true;
    
    // 使用单线程线程池来执行阻塞监听
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 启动任务状态回调监听器...");
        executorService.submit(this::consumeMessage);
    }

    private void consumeMessage() {
        while (running) {
            try {
                // 阻塞式右侧弹出，超时时间 5 秒
                String message = stringRedisTemplate.opsForList().rightPop(BuffConstant.REDIS_QUEUE_TASK_STATUS, 5, TimeUnit.SECONDS);
                if (message != null) {
                    log.info("收到任务状态变更消息: {}", message);
                    BuffScanTask task = JSONUtil.toBean(message, BuffScanTask.class);
                    buffScanTaskService.taskCallback(task);
                }
            } catch (Exception e) {
                log.error("消费任务状态消息异常", e);
                try {
                    // 避免异常导致的死循环过快
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    
    // Spring 容器销毁时停止线程
    public void destroy() {
        this.running = false;
        executorService.shutdown();
    }
}
