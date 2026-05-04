package com.niro.web.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.niro.web.config.PostgresJsonTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C5 市场价格最新快照实体。
 */
@Data
@TableName(value = "c5_market_price_snapshot", autoResultMap = true)
public class C5MarketPriceSnapshot {

    /**
     * 主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Steam 应用 ID。
     */
    private Integer appId;

    /**
     * Steam 市场 Hash 名称。
     */
    private String marketHashName;

    /**
     * 区间类型：ALL / WEAR。
     */
    private String rangeType;

    /**
     * 归一化查询最小磨损。
     */
    private BigDecimal wearMin;

    /**
     * 归一化查询最大磨损。
     */
    private BigDecimal wearMax;

    /**
     * C5 查询页码。
     */
    private Integer pageNum;

    /**
     * C5 查询页大小。
     */
    private Integer pageSize;

    /**
     * 当前快照最低价。
     */
    private BigDecimal lowestPrice;

    /**
     * 当前快照样本算术平均价。
     */
    private BigDecimal avgPrice;

    /**
     * 当前快照样本数量。
     */
    private Integer sampleCount;

    /**
     * C5 是否还有更多数据。
     */
    private Boolean hasMore;

    /**
     * 精简挂单列表 JSON。
     */
    @TableField(typeHandler = PostgresJsonTypeHandler.class, jdbcType = JdbcType.OTHER)
    private Object listingsJson;

    /**
     * 是否启用刷新。
     */
    private Boolean refreshEnabled;

    /**
     * 临时刷新优先级。
     */
    private Integer refreshPriority;

    /**
     * 目标刷新间隔秒数。
     */
    private Integer refreshIntervalSeconds;

    /**
     * 下次应刷新时间。
     */
    private LocalDateTime nextRefreshTime;

    /**
     * 最近一次尝试刷新时间。
     */
    private LocalDateTime lastFetchTime;

    /**
     * 最近一次成功刷新时间。
     */
    private LocalDateTime lastSuccessTime;

    /**
     * 最近一次被前端或业务关注的时间。
     */
    private LocalDateTime lastRequestTime;

    /**
     * 刷新状态。
     */
    private String status;

    /**
     * 累计刷新次数。
     */
    private Long fetchCount;

    /**
     * 连续失败次数。
     */
    private Integer failCount;

    /**
     * 最近失败原因摘要。
     */
    private String lastErrorMessage;

    /**
     * 最近执行刷新所使用的系统 C5 市场查询账号 ID。
     */
    private Long lastFetchAccountId;

    /**
     * 最近一次开始刷新时间。
     */
    private LocalDateTime refreshStartTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
