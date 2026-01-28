package com.niro.sdk.c5.response.trade;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5BatchBuyResponse {
    @JsonProperty("balance")
    private BigDecimal balance;
    @JsonProperty("failed_list")
    private List<FailedItem> failedList;
    @JsonProperty("failed_amount")
    private BigDecimal failedAmount;
    @JsonProperty("success_num")
    private Integer successNum;
    @JsonProperty("fail_num")
    private Integer failNum;
    @JsonProperty("success_list")
    private List<SuccessItem> successList;

    @Data
    public static class FailedItem {
        @JsonProperty("product_id")
        private String productId;
        @JsonProperty("out_trade_no")
        private String outTradeNo;
        @JsonProperty("amount")
        private BigDecimal amount;
        
        @JsonProperty("code")
        private Integer errorCode;
        
        @JsonProperty("msg")
        private String errorMsg;
    }

    @Data
    public static class SuccessItem {
        @JsonProperty("out_trade_no")
        private String outTradeNo;
        @JsonProperty("product_id")
        private String productId;
        @JsonProperty("actual_pay")
        private BigDecimal actualPay;
        @JsonProperty("delivery")
        private Integer delivery;
        @JsonProperty("order_asset_id")
        private String orderAssetId;
        @JsonProperty("order_id")
        private String orderId;
    }
}
