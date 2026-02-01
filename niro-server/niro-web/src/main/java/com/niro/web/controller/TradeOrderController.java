package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.sdk.c5.response.trade.C5OrderDetailResponse;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.service.TradeOrderRecordService;
import com.niro.web.vo.C5OrderDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 交易订单记录控制器
 *
 * @author niro
 * @since 2026-01-22
 */
@Tag(name = "交易订单记录")
@RestController
@RequestMapping("/order/record")
@RequiredArgsConstructor
public class TradeOrderController {

    private final TradeOrderRecordService tradeOrderRecordService;

    @Operation(summary = "分页查询订单记录")
    @GetMapping("/page")
    public Page<TradeOrderRecordDTO> getOrderRecordPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {

        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getOrderRecordPage(page, pageSize, platform, status, userId, keyword, sortField, sortOrder);
    }

    @Operation(summary = "获取 C5 订单详情")
    @GetMapping("/c5/detail/{orderId}")
    public C5OrderDetailVO getC5OrderDetail(@PathVariable String orderId) {
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getC5OrderDetail(userId, orderId);
    }

    @Operation(summary = "删除订单记录")
    @DeleteMapping("/{id}")
    public void deleteOrderRecord(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        tradeOrderRecordService.deleteOrderRecord(userId, id);
    }

    @Operation(summary = "更新订单记录")
    @PutMapping
    public void updateOrderRecord(@RequestBody TradeOrderRecordDTO dto) {
        tradeOrderRecordService.updateOrderRecord(dto);
    }
}
