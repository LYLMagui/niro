package com.niro.sdk.c5.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * C5 订单详情响应 (v2)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class C5OrderDetailResponse {
    /**
     * 订单号
     */
    private String orderId;

    /**
     * 商品ID
     */
    private String productId;

    /**
     * 价格 (v2字段)
     */
    private BigDecimal price;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 发货类型
     */
    private Integer deliverType;

    /**
     * 接收方 Steam ID
     */
    private String receiveSteamId;

    /**
     * 创建时间戳
     */
    private Long createTime;
    
    /**
     * 失败原因
     */
    private String failedDesc;
    
    /**
     * 商品信息
     */
    private OpenItemInfo openItemInfo;
    
    /**
     * 报价信息
     */
    private OfferInfoDTO offerInfoDTO;

    /**
     * 饰品信息 (扩展信息)
     */
    private Map<String, Object> assetInfo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenItemInfo {
        private String itemId;
        private Integer appId;
        private String name;
        private String marketHashName;
        private String imageUrl;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfferInfoDTO {
        private String transferId;
        private String tradeOfferId;
        private Long deliverTime;
        private Long acceptExpireTime;
    }

    // --- 兼容旧版字段及前端展示 ---

    public String getGoodsName() {
        return openItemInfo != null ? openItemInfo.getName() : null;
    }

    public String getGoodsImg() {
        return openItemInfo != null ? openItemInfo.getImageUrl() : null;
    }

    public BigDecimal getActualPay() {
        return price;
    }
    
    public Integer getPayStatus() {
        // v2 接口无 payStatus，但如果 status=10 (success)，则认为已支付 (1)
        if (status != null && status == 10) {
            return 1;
        }
        return 0;
    }

    // 将 assetInfo 暴露为 extra 以兼容前端
    public Map<String, Object> getExtra() {
        return assetInfo;
    }
}
