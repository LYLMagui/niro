package com.niro.web.jobhandler;

import com.niro.web.service.C5MarketPriceSnapshotService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * C5 市场价格快照刷新任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class C5MarketPriceSnapshotJobHandler {

    private final C5MarketPriceSnapshotService marketPriceSnapshotService;

    /**
     * 扫描到期价格快照并投递刷新队列。
     */
    @XxlJob("scanC5MarketPriceSnapshots")
    public void scanC5MarketPriceSnapshots() {
        try {
            int enqueued = marketPriceSnapshotService.scanAndEnqueueDueSnapshots();
            XxlJobHelper.handleSuccess("C5市场价格快照入队数量: " + enqueued);
        } catch (Exception e) {
            log.error("C5市场价格快照扫描任务失败", e);
            XxlJobHelper.handleFail("C5市场价格快照扫描失败: " + e.getMessage());
        }
    }
}
