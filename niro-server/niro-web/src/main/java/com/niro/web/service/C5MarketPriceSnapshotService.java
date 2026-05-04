package com.niro.web.service;

import com.niro.web.dto.C5MarketPriceSnapshotReferenceDTO;
import com.niro.web.dto.param.C5MarketPriceSnapshotReferenceParam;
import com.niro.web.dto.param.C5MarketPriceSnapshotRefreshRequestParam;

/**
 * C5 市场价格快照服务。
 */
public interface C5MarketPriceSnapshotService {

    /**
     * 查询本地价格参考快照。
     *
     * @param param 查询参数
     * @return 快照参考结果
     */
    C5MarketPriceSnapshotReferenceDTO getReference(C5MarketPriceSnapshotReferenceParam param);

    /**
     * 手动申请刷新价格快照。
     *
     * @param param 刷新参数
     * @return 快照参考结果
     */
    C5MarketPriceSnapshotReferenceDTO requestRefresh(C5MarketPriceSnapshotRefreshRequestParam param);

    /**
     * 扫描到期快照并投递刷新队列。
     *
     * @return 入队数量
     */
    int scanAndEnqueueDueSnapshots();

    /**
     * 消费单条刷新快照消息。
     *
     * @param snapshotId 快照 ID
     */
    void consumeRefreshSnapshot(Long snapshotId);
}
