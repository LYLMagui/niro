package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.enums.C5SnipingAccountStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C5 扫货独立账号实体。
 * <p>
 * 对应 {@code c5_sniping_account} 表，只服务 C5 扫货 2.0 任务绑定、检测和运行统计，
 * 不复用 BUFF 账号的扫描号、下单号、全能号角色语义。
 * </p>
 */
@Data
@TableName("c5_sniping_account")
public class C5SnipingAccount {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统用户 ID。
     */
    private Long userId;

    /**
     * 账号展示名称。
     */
    private String accountName;

    /**
     * C5 AppKey。
     */
    private String c5AppKey;

    /**
     * C5 AppKey 密文。
     */
    private String c5AppKeyEncrypted;

    /**
     * C5 AppKey 脱敏展示值。
     */
    private String c5AppKeyMasked;

    /**
     * C5 AppKey 历史明文迁移时间。
     */
    private LocalDateTime c5AppKeyMigratedAt;

    /**
     * Steam 交易链接。
     */
    private String steamTradeUrl;

    /**
     * Steam ID，用于 C5 库存接口。
     */
    private String steamId;

    /**
     * 账号状态。
     */
    private C5SnipingAccountStatusEnum status;

    /**
     * C5 可用余额。
     */
    private BigDecimal balance;

    /**
     * 交易待结算余额。
     */
    private BigDecimal pendingBalance;

    /**
     * 保证金余额。
     */
    private BigDecimal depositAmount;

    /**
     * 秒到账余额。
     */
    private BigDecimal creditMoney;

    /**
     * 秒到账保证金。
     */
    private BigDecimal creditDeposit;

    /**
     * 最近一次检测时间。
     */
    private LocalDateTime lastCheckTime;

    /**
     * 用户备注。
     */
    private String remark;

    /**
     * 当前账号警告信息。
     */
    private String warningMsg;

    /**
     * 今日扫描次数。
     */
    private Integer todayScanCount;

    /**
     * 成功下单次数。
     */
    private Integer tradeSuccessCount;

    /**
     * 总下单次数。
     */
    private Integer tradeTotalCount;

    /**
     * 逻辑删除标记：0-未删除，1-已删除。
     */
    @TableLogic
    private Integer delFlag;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
