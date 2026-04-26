package com.niro.web.service.impl;

import com.niro.web.dto.C5SnipingTaskV2EventDTO;
import com.niro.web.service.C5SnipingTaskV2EventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C5 扫货 2.0 运行态事件服务实现。
 */
@Slf4j
@Service
public class C5SnipingTaskV2EventServiceImpl implements C5SnipingTaskV2EventService {

    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 订阅指定用户的运行态事件。
     *
     * @param userId 用户 ID
     * @return SSE emitter
     */
    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String emitterId = UUID.randomUUID().toString();
        emitters.computeIfAbsent(userId, key -> new ConcurrentHashMap<>()).put(emitterId, emitter);
        emitter.onTimeout(() -> removeEmitter(userId, emitterId));
        emitter.onCompletion(() -> removeEmitter(userId, emitterId));
        emitter.onError(e -> removeEmitter(userId, emitterId));
        send(userId, emitterId, emitter, C5SnipingTaskV2EventDTO.builder()
                .eventType("CONNECTED")
                .occurredAt(LocalDateTime.now())
                .message("connected")
                .build());
        return emitter;
    }

    /**
     * 发布用户维度运行态事件。
     *
     * @param userId 用户 ID
     * @param event 事件内容
     */
    @Override
    public void publish(Long userId, C5SnipingTaskV2EventDTO event) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        Set<Map.Entry<String, SseEmitter>> entries = userEmitters.entrySet();
        for (Map.Entry<String, SseEmitter> entry : entries) {
            send(userId, entry.getKey(), entry.getValue(), event);
        }
    }

    private void send(Long userId, String emitterId, SseEmitter emitter, C5SnipingTaskV2EventDTO event) {
        try {
            emitter.send(SseEmitter.event().data(event));
        } catch (IOException | IllegalStateException e) {
            log.debug("C5扫货2.0 SSE发送失败: userId={}, emitterId={}", userId, emitterId, e);
            removeEmitter(userId, emitterId);
        }
    }

    private void removeEmitter(Long userId, String emitterId) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        userEmitters.remove(emitterId);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
        }
    }
}
