package com.niro.sdk.c5.trade;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5BatchBuyResponse {
    private BigDecimal balance;
    private List<FailedItem> failedList;
    private BigDecimal failedAmount;
    private Integer successNum;
    private Integer failNum;
    private List<SuccessItem> successList;

    @Data
    public static class FailedItem {
        private String productId;
        private String outTradeNo;
        private BigDecimal amount;
        
        @JSONField(name = "code")
        private Integer errorCode;
        
        @JSONField(name = "msg")
        private String errorMsg;
    }

    @Data
    public static class SuccessItem {
        private String outTradeNo;
        private String productId;
        private BigDecimal actualPay;
        private Integer delivery;
        private String orderAssetId;
        private String orderId;
    }
}
