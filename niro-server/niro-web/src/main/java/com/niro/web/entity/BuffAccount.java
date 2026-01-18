package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.niro.web.enums.BuffAccountRoleEnum;
import com.niro.web.enums.BuffAccountStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BUFF 账号配置实体
 *
 * @author niro
 * @since 2026-01-15
 */
@Data
@TableName("buff_account")
public class BuffAccount {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统用户id
     */
    private Long userId;

    /**
     * 账号备注名
     */
    private String accountName;

    /**
     * BUFF 登录凭证
     */
    private String buffCookie;

    /**
     * 账号角色
     */
    private BuffAccountRoleEnum role;

    /**
     * 账号状态
     */
    private BuffAccountStatusEnum status;

    /**
     * 调度权重
     */
    private Integer weight;

    /**
     * 账号余额
     */
    private BigDecimal balance;

    /**
     * 连续请求失败计数
     */
    private Integer failCount;

    /**
     * 最后有效性检测时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 关联的浏览器指纹
     */
    private String userAgent;

    /**
     * 用户自定义备注信息
     */
    private String remark;

    /**
     * 异常说明
     */
    private String warningMsg;

    /**
     * 今日扫货总数
     */
    private Integer todayScanCount;

    /**
     * 下单成功总数
     */
    private Integer tradeSuccessCount;

    /**
     * 下单总数（用于计算成功率）
     */
    private Integer tradeTotalCount;

    /**
     * 代理服务器 (host:port)
     */
    @TableField(exist = false)
    private String proxy;

    /**
     * 抓取频率限制 (QPS)
     */
    @TableField(exist = false)
    private Double frequency;

    /**
     * 逻辑删除标记：0-未删除, 1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
