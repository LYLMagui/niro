package com.niro.web.jobhandler;

import com.niro.web.service.C5OrderSyncService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class C5OrderPullJobHandler {

    private final C5OrderSyncService c5OrderSyncService;

    /**
     * 拉取 C5 平台订单
     * 调度参数: daysBefore (默认 0，表示当天)
     * 示例: 0 表示今天, 1 表示昨天, -1 表示全部历史
     */
    @XxlJob("c5OrderPullJobHandler")
    public void pullC5Orders() {
        String jobParam = XxlJobHelper.getJobParam();
        int daysBefore = 0;

        if (jobParam != null && !jobParam.trim().isEmpty()) {
            try {
                daysBefore = Integer.parseInt(jobParam.trim());
            } catch (NumberFormatException e) {
                log.warn("无效的调度参数: {}, 使用默认值 0", jobParam);
            }
        }

        String startMsg = StrUtil.format("开始拉取 C5 订单, daysBefore={}", daysBefore);
        XxlJobHelper.log(startMsg);
        log.info(startMsg);

        try {
            c5OrderSyncService.syncOrders(daysBefore);

            String successMsg = "C5 订单拉取完成";
            XxlJobHelper.log(successMsg);
            log.info(successMsg);

        } catch (Exception e) {
            String errorMsg = "C5 订单拉取失败: " + e.getMessage();
            XxlJobHelper.handleFail(errorMsg);
            log.error(errorMsg, e);
        }
    }
}
