package com.niro.sdk.c5.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class C5GoodsSearchResponse {
    private Integer limit;
    private Integer page;
    private Integer pages;
    private Long total;
    private List<C5GoodsInfo> list;

    @Data
    public static class C5GoodsInfo {
        private String id;
        private Integer appId;
        private String itemId;
        private String itemName;
        private String marketHashName;
        private BigDecimal price;
        private BigDecimal cnyPrice;
        private Integer delivery;
        // 其他字段可根据需要补充
    }
}
