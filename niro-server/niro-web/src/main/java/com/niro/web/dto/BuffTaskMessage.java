package com.niro.web.dto;

import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.TaskRunModeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * BUFF 任务消息 DTO (发送至 Redis 队列)
 *
 * @author niro
 * @since 2026-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuffTaskMessage {

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 运行模式
     */
    private TaskRunModeEnum runMode;

    /**
     * 任务类型 (1: 炼金扫货, 2: 站内倒卖, 10: 分类商品同步)
     */
    private Integer taskType;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 关联的下单任务ID (仅 SCAN/BOTH 模式使用)
     */
    private Long targetTaskId;

    /**
     * 商品ID / 分类ID
     */
    private Long goodsId;

    /**
     * 最高价格 (炼金模式)
     */
    private BigDecimal maxPrice;

    /**
     * 最小利润 (倒卖模式)
     */
    private BigDecimal minProfit;

    /**
     * 扫描间隔(秒) - 最小值
     */
    private Integer scanIntervalMin;

    /**
     * 扫描间隔(秒) - 最大值
     */
    private Integer scanIntervalMax;

    /**
     * 持续时间(分钟) / 工作周期
     */
    private Integer durationMinutes;

    /**
     * 休息时间(分钟)
     */
    private Integer restPeriod;

    /**
     * 任务所属用户ID
     */
    private Long userId;

    /**
     * 分片分类ID列表 (用于分片抓取)
     */
    private List<Long> categoryIds;

    /**
     * 分类元数据 (Map<categoryId, {name: "xxx", internalName: "xxx"}>)
     * 用于前端直接下发元数据，避免 Spider 再次查询数据库
     */
    private Map<String, Map<String, String>> categoryMeta;

    /**
     * 分片页码范围 (用于分片抓取)
     */
    private List<Integer> pageRange;

    /**
     * 计划购买数量
     */
    private Integer buyCount;

    /**
     * 已成功购买数量
     */
    private Integer successCount;

    /**
     * 绑定的账号列表
     */
    private List<AccountContext> accounts;

    /**
     * 支付方式 (BALANCE, BUFF_BALANCE, ALIPAY, WECHAT)
     */
    private String paymentMethod;

    /**
     * 允许下单的账号ID列表 (如果为空，则为仅扫描模式)
     */
    private List<Long> execAccountIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountContext {
        /**
         * 账号ID
         */
        private Long accountId;

        /**
         * 账号名称
         */
        private String accountName;

        /**
         * BUFF Cookie
         */
        private String buffCookie;

        /**
         * 代理服务器地址 (host:port)
         */
        private String proxy;

        /**
         * 账号角色
         */
        private BuffAccountRoleEnum role;

        /**
         * 浏览器指纹
         */
        private String userAgent;

        /**
         * 抓取频率限制 (QPS)
         */
        private Double frequency;
    }
}
