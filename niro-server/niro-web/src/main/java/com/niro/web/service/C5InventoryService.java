package com.niro.web.service;

import com.niro.web.dto.C5InventoryPageDTO;
import com.niro.web.dto.C5InventoryRefreshResultDTO;
import com.niro.web.dto.param.C5InventoryQueryParam;
import com.niro.web.dto.param.C5InventoryRefreshParam;

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
}
