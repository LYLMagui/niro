package com.niro.web.controller;

import com.niro.web.service.C5OrderSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C5 订单同步控制器
 * <p>
 * 提供手动触发 C5 订单同步的接口
 *
 * @author niro
 * @since 2026-02-10
 */
@Slf4j
@RestController
@RequestMapping("/api/c5/order-sync")
@RequiredArgsConstructor
@Tag(name = "C5 订单同步", description = "C5 平台订单同步管理")
public class C5OrderSyncController {

    private final C5OrderSyncService c5OrderSyncService;

    /**
     * 手动触发 C5 订单同步
     *
     * @param daysBefore 查询几天前的订单，0 表示今天，1 表示昨天，-1 表示全部历史，默认为 1
     * @return 同步结果
     */
    @PostMapping("/trigger")
    @Operation(summary = "手动同步 C5 订单", description = "手动触发 C5 平台订单同步任务")
    public String triggerSync(
            @Parameter(description = "查询几天前的订单，0=今天，1=昨天，-1=全部历史，默认1")
            @RequestParam(defaultValue = "1") Integer daysBefore) {
        log.info("手动触发 C5 订单同步, daysBefore={}", daysBefore);
        try {
            c5OrderSyncService.syncOrders(daysBefore);
            return "C5 订单同步任务已启动";
        } catch (Exception e) {
            log.error("手动触发 C5 订单同步失败", e);
            return "同步失败: " + e.getMessage();
        }
    }
}
