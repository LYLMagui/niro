package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.C5InventoryAssetPageDTO;
import com.niro.web.dto.C5InventoryListingFeeDTO;
import com.niro.web.dto.C5InventoryListingResultDTO;
import com.niro.web.dto.C5InventoryMarketReferencePageDTO;
import com.niro.web.dto.C5InventoryPageDTO;
import com.niro.web.dto.C5InventoryRefreshResultDTO;
import com.niro.web.dto.C5InventoryStatsDTO;
import com.niro.web.dto.param.C5InventoryItemListParam;
import com.niro.web.dto.param.C5InventoryListingCreateParam;
import com.niro.web.dto.param.C5InventoryListingFeeBatchCalculateParam;
import com.niro.web.dto.param.C5InventoryListingFeeCalculateParam;
import com.niro.web.dto.param.C5InventoryMarketReferenceParam;
import com.niro.web.dto.param.C5InventoryQueryParam;
import com.niro.web.dto.param.C5InventoryRefreshParam;
import com.niro.web.service.C5InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C5 库存管理接口。
 */
@Tag(name = "C5库存管理")
@RestController
@RequestMapping("/api/c5/inventory")
@RequiredArgsConstructor
@SaCheckLogin
public class C5InventoryController {

    private final C5InventoryService c5InventoryService;

    /**
     * 刷新指定 C5 扫货账号库存。
     *
     * @param param 刷新参数
     * @return 刷新结果
     */
    @PostMapping("/refresh")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_REFRESH)
    @Operation(summary = "刷新C5库存")
    public C5InventoryRefreshResultDTO refreshInventory(@RequestBody @Valid C5InventoryRefreshParam param) {
        return c5InventoryService.refreshInventory(param);
    }

    /**
     * 分页查询 C5 库存。
     *
     * @param param 查询参数
     * @return 库存分页
     */
    @GetMapping
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "分页查询C5库存")
    public C5InventoryPageDTO pageInventory(@Valid C5InventoryQueryParam param) {
        return c5InventoryService.pageInventory(param);
    }

    /**
     * 统计 C5 库存状态数量。
     *
     * @param param 查询参数
     * @return 库存状态数量
     */
    @GetMapping("/stats")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "统计C5库存状态数量")
    public C5InventoryStatsDTO statsInventory(@Valid C5InventoryQueryParam param) {
        return c5InventoryService.statsInventory(param);
    }

    /**
     * 分页查询 C5 库存真实资产明细。
     *
     * @param param 查询参数
     * @return 库存明细分页
     */
    @GetMapping("/items")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "分页查询C5库存明细")
    public C5InventoryAssetPageDTO pageInventoryItems(@Valid C5InventoryItemListParam param) {
        return c5InventoryService.pageInventoryItems(param);
    }

    /**
     * 提交 C5 库存饰品上架。
     *
     * @param param 上架参数
     * @return 上架结果
     */
    @PostMapping("/listings")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_REFRESH)
    @Operation(summary = "提交C5库存上架")
    public C5InventoryListingResultDTO createInventoryListings(@RequestBody @Valid C5InventoryListingCreateParam param) {
        return c5InventoryService.createInventoryListings(param);
    }

    /**
     * 查询 C5 同平台在售参考。
     *
     * @param param 查询参数
     * @return 在售参考分页
     */
    @GetMapping("/market-references")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "查询C5同平台在售参考")
    public C5InventoryMarketReferencePageDTO listMarketReferences(@Valid C5InventoryMarketReferenceParam param) {
        return c5InventoryService.listMarketReferences(param);
    }


    /**
     * 刷新 C5 同平台在售参考。
     *
     * @param param 查询参数
     * @return 在售参考分页
     */
    @PostMapping("/market-references/refresh")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "刷新C5同平台在售参考")
    public C5InventoryMarketReferencePageDTO refreshMarketReferences(@RequestBody @Valid C5InventoryMarketReferenceParam param) {
        return c5InventoryService.refreshMarketReferences(param);
    }

    /**
     * 计算 C5 库存上架手续费。
     *
     * @param param 计算参数
     * @return 手续费结果
     */
    @PostMapping("/listing-fee")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "计算C5库存上架手续费")
    public C5InventoryListingFeeDTO calculateListingFee(@RequestBody @Valid C5InventoryListingFeeCalculateParam param) {
        return c5InventoryService.calculateListingFee(param);
    }

    /**
     * 批量计算 C5 库存上架手续费。
     *
     * @param param 计算参数
     * @return 手续费结果
     */
    @PostMapping("/listing-fees")
    @SaCheckPermission(PermissionConstants.C5_INVENTORY_LIST)
    @Operation(summary = "批量计算C5库存上架手续费")
    public List<C5InventoryListingFeeDTO> calculateListingFees(@RequestBody @Valid C5InventoryListingFeeBatchCalculateParam param) {
        return c5InventoryService.calculateListingFees(param);
    }
}
