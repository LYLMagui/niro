package com.niro.web.jobhandler;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * XXL-JOB 测试任务
 */
@Slf4j
@Component
public class TestJobHandler {

    /**
     * 1、简单任务示例（Bean模式）
     */
    @XxlJob("testJobHandler")
    public void testJobHandler() throws Exception {
        XxlJobHelper.log("XXL-JOB, Hello World.");
        log.info("XXL-JOB 测试任务开始执行...");

        for (int i = 0; i < 5; i++) {
            XxlJobHelper.log("beat at:" + i);
            log.info("测试任务执行中: {}", i);
            TimeUnit.SECONDS.sleep(1);
        }
        
        // 获取参数
        String param = XxlJobHelper.getJobParam();
        XxlJobHelper.log("任务参数: " + param);
        log.info("任务参数: {}", param);

        XxlJobHelper.handleSuccess();
        log.info("XXL-JOB 测试任务执行结束");
    }

    /**
     * 2、分片广播任务
     */
    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() throws Exception {
        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);
        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 业务逻辑
        for (int i = 0; i < shardTotal; i++) {
            if (i == shardIndex) {
                XxlJobHelper.log("第 {} 片, 命中分片开始处理", i);
            } else {
                XxlJobHelper.log("第 {} 片, 忽略", i);
            }
        }
    }
}
