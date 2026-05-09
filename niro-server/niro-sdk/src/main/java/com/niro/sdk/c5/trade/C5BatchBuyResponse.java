package com.niro.sdk.c5.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        
        @JsonProperty("code")
        private Integer errorCode;
        
        @JsonProperty("msg")
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
