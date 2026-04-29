package com.niro.web.service;

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

import java.util.List;

/**
 * C5 库存管理服务。
 */
public interface C5InventoryService {

    /**
     * 刷新当前用户指定账号的 C5 库存快照。
     *
     * @param param 刷新参数
     * @return 刷新结果
     */
    C5InventoryRefreshResultDTO refreshInventory(C5InventoryRefreshParam param);

    /**
     * 分页查询当前用户 C5 库存快照。
     *
     * @param param 查询参数
     * @return 库存分页
     */
    C5InventoryPageDTO pageInventory(C5InventoryQueryParam param);

    /**
     * 统计当前用户 C5 库存状态数量。
     *
     * @param param 查询参数
     * @return 状态数量统计
     */
    C5InventoryStatsDTO statsInventory(C5InventoryQueryParam param);

    /**
     * 分页查询聚合卡片背后的真实库存明细。
     *
     * @param param 查询参数
     * @return 库存明细分页
     */
    C5InventoryAssetPageDTO pageInventoryItems(C5InventoryItemListParam param);

    /**
     * 提交库存饰品上架。
     *
     * @param param 上架参数
     * @return 上架结果
     */
    C5InventoryListingResultDTO createInventoryListings(C5InventoryListingCreateParam param);

    /**
     * 查询 C5 同平台在售参考。
     *
     * @param param 查询参数
     * @return 在售参考分页
     */
    C5InventoryMarketReferencePageDTO listMarketReferences(C5InventoryMarketReferenceParam param);

    /**
     * 计算 C5 上架手续费。
     *
     * @param param 计算参数
     * @return 手续费结果
     */
    C5InventoryListingFeeDTO calculateListingFee(C5InventoryListingFeeCalculateParam param);

    /**
     * 批量计算 C5 上架手续费。
     *
     * @param param 计算参数
     * @return 手续费结果
     */
    List<C5InventoryListingFeeDTO> calculateListingFees(C5InventoryListingFeeBatchCalculateParam param);
}
