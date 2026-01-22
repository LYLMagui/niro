package com.niro.web.scheduler;

import com.niro.web.service.TradeOrderRecordService;
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
 * Redis 订单消息监听器
 *
 * @author niro
 * @since 2026-01-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeOrderConsumer implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final TradeOrderRecordService tradeOrderRecordService;

    private static final String REDIS_KEY_ORDER_REPORT = "niro:order:report";
    private volatile boolean running = true;
    
    // 使用单线程线程池来执行阻塞监听
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 启动订单上报消息监听器...");
        executorService.submit(this::consumeMessage);
    }

    private void consumeMessage() {
        while (running) {
            try {
                // 阻塞式右侧弹出，超时时间 5 秒
                String message = stringRedisTemplate.opsForList().rightPop(REDIS_KEY_ORDER_REPORT, 5, TimeUnit.SECONDS);
                if (message != null) {
                    log.debug("收到订单上报消息: {}", message);
                    tradeOrderRecordService.handleOrderReport(message);
                }
            } catch (Exception e) {
                log.error("消费订单消息异常", e);
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
