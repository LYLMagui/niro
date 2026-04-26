package com.niro.web.service;

import com.niro.web.dto.C5SnipingTaskV2EventDTO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * C5 扫货 2.0 运行态事件服务。
 */
public interface C5SnipingTaskV2EventService {

    /**
     * 订阅指定用户的运行态事件。
     *
     * @param userId 用户 ID
     * @return SSE emitter
     */
    SseEmitter subscribe(Long userId);

    /**
     * 发布用户维度运行态事件。
     *
     * @param userId 用户 ID
     * @param event 事件内容
     */
    void publish(Long userId, C5SnipingTaskV2EventDTO event);
}
