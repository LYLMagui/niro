package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.service.TradeOrderRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getOrderRecordPage(pageNum, pageSize, platform, status, userId, keyword);
    }
}
