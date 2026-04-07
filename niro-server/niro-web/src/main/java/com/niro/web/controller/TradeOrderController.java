package com.niro.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.InventoryItemDTO;
import com.niro.web.dto.PurchaseStatsItemDTO;
import com.niro.web.dto.PurchaseStatsSplitItemDTO;
import com.niro.web.dto.PurchaseStatsSummaryDTO;
import com.niro.web.dto.PurchaseStatsTrendDTO;
import com.niro.web.dto.TradeOrderRecordDTO;
import com.niro.web.dto.param.TradeOrderBatchDeleteParam;
import com.niro.web.service.TradeOrderRecordService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    @SaCheckPermission(PermissionConstants.TASK_RECORD_LIST)
    public Page<TradeOrderRecordDTO> getOrderRecordPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {

        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getOrderRecordPage(page, pageSize, status, userId, keyword, startDate, endDate, sortField, sortOrder);
    }

    @Operation(summary = "删除订单记录")
    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionConstants.ORDER_RECORD_DELETE)
    public void deleteOrderRecord(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        tradeOrderRecordService.deleteOrderRecord(userId, id);
    }

    @Operation(summary = "批量删除订单记录")
    @PostMapping("/batch-delete")
    @SaCheckPermission(PermissionConstants.ORDER_RECORD_DELETE)
    public void batchDeleteOrderRecord(@RequestBody TradeOrderBatchDeleteParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        tradeOrderRecordService.batchDeleteOrderRecord(userId, param.getIds());
    }

    @Operation(summary = "更新订单记录")
    @PutMapping
    @SaCheckPermission(PermissionConstants.ORDER_RECORD_UPDATE)
    public void updateOrderRecord(@RequestBody TradeOrderRecordDTO dto) {
        tradeOrderRecordService.updateOrderRecord(dto);
    }

    @Operation(summary = "获取库存看板数据")
    @GetMapping("/inventory")
    @SaCheckPermission(PermissionConstants.TASK_INVENTORY_VIEW)
    public List<InventoryItemDTO> getInventoryItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getInventoryItems(userId, keyword, startDate, endDate);
    }

    @Operation(summary = "获取购买统计汇总")
    @GetMapping("/purchase-stats/summary")
    @SaCheckPermission(PermissionConstants.TASK_INVENTORY_VIEW)
    public PurchaseStatsSummaryDTO getPurchaseStatsSummary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getPurchaseStatsSummary(userId, keyword, startDate, endDate);
    }

    @Operation(summary = "获取购买统计趋势")
    @GetMapping("/purchase-stats/trend")
    @SaCheckPermission(PermissionConstants.TASK_INVENTORY_VIEW)
    public List<PurchaseStatsTrendDTO> getPurchaseStatsTrend(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getPurchaseStatsTrend(userId, keyword, startDate, endDate);
    }

    @Operation(summary = "获取购买统计商品明细")
    @GetMapping("/purchase-stats/items")
    @SaCheckPermission(PermissionConstants.TASK_INVENTORY_VIEW)
    public List<PurchaseStatsItemDTO> getPurchaseStatsItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getPurchaseStatsItems(userId, keyword, startDate, endDate);
    }

    @Operation(summary = "获取购买统计按时间拆分明细")
    @GetMapping("/purchase-stats/split-items")
    @SaCheckPermission(PermissionConstants.TASK_INVENTORY_VIEW)
    public List<PurchaseStatsSplitItemDTO> getPurchaseStatsSplitItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return tradeOrderRecordService.getPurchaseStatsSplitItems(userId, keyword, startDate, endDate);
    }
}
